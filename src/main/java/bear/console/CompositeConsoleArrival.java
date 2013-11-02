package bear.console;

import chaschev.util.Exceptions;
import chaschev.lang.LangUtils;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.StringUtils.getLevenshteinDistance;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class CompositeConsoleArrival<ENTRY> {
    private final List<ListenableFuture<ENTRY>> futures;
    private final List<? extends AbstractConsole> consoles;

    protected Future<EqualityGroups> groups;

    protected List<ENTRY> entries;
    protected List<ENTRY> arrivedEntries;

    protected String[] convertedEntries;

    protected Function<ENTRY, String> entryAsText;
    protected Function<ENTRY, String> entryId;

    protected double thresholdDistancePct = 5;

    public CompositeConsoleArrival(
        List<ENTRY> entries, List<ListenableFuture<ENTRY>> futures, List<? extends AbstractConsole> consoles,
        Function<ENTRY, String> entryAsText, Function<ENTRY, String> entryId) {

        this.entries = entries;

        this.futures = futures;
        this.consoles = consoles;
        this.entryAsText = entryAsText;
        this.entryId = entryId;

        arrivedEntries = LangUtils.newFilledArrayList(entries.size(), null);
        convertedEntries = new String[entries.size()];
    }

    public void addArrival(int i, ENTRY entry) {
        arrivedEntries.set(i, entry);
    }

    public void await(int sec) {
        try {
            Futures.successfulAsList(futures).get(sec, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }


    public static class EqualityGroups {
        public List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

        int size;

        public EqualityGroups(List<EqualityGroup> groups) {
            this.groups = groups;

            for (EqualityGroup group : groups) {
                size += group.size();
            }
        }

        public Optional<EqualityGroup> getMajorityGroup() {
            return Iterables.tryFind(groups, new Predicate<EqualityGroup>() {
                @Override
                public boolean apply(EqualityGroup input) {
                    return input.size() > size / 2;
                }
            });
        }

        public ArrayList<EqualityGroup> getMinorGroups() {
            return newArrayList(filter(groups, not(equalTo(getMajorityGroup().orNull()))));
        }
    }

    public static class EqualityGroup<ENTRY> implements Comparable<EqualityGroup<?>>{
        public String id;
        public String text;
        public int firstEntry;
        public  final double thresholdDistancePct;
        public List<String> entriesIds = new ArrayList<String>();
        public int distance;

        private EqualityGroup(String id, String text, int firstEntry, double thresholdDistancePct) {
            this.id = id;
            this.text = text;
            this.firstEntry = firstEntry;
            this.thresholdDistancePct = thresholdDistancePct;
        }

        public boolean sameGroup(double distance) {
            return distance < thresholdDistancePct;
        }

        private double distancePct(String otherText) {
            return getLevenshteinDistance(text, otherText, 5000) * 100.0 /
                (text.length() + otherText.length());
        }

        public int size() {
            return entriesIds.size() + 1;
        }

        public void add(String id) {
            entriesIds.add(id);
        }

        @Override
        public int compareTo(EqualityGroup<?> o) {
            return size() - o.size();
        }
    }

    public List<EqualityGroup> divideIntoGroups() {
        for (int i = 0; i < futures.size(); i++) {
            convertedEntries[i] = entryAsText.apply(arrivedEntries.get(i));
        }

        List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

        groups.add(newGroupByIndex(0));

        for (int i = 1; i < convertedEntries.length; i++) {
            String entryText = convertedEntries[i];

            boolean foundGroup = false;
            double distance = 0;

            for (EqualityGroup group : groups) {
                distance = group.distancePct(entryText);
                if (group.sameGroup(distance)) {
                    group.add(entryId.apply(entries.get(i)));
                    foundGroup = true;
                    break;
                }
            }

            if(!foundGroup){
                EqualityGroup group = newGroupByIndex(i);
                groups.add(group);
                group.distance = (int) distance;
            }
        }

//        EqualityGroups equalityGroups = new EqualityGroups(groups);

        Collections.sort(groups);

        return groups;
    }

    private EqualityGroup newGroupByIndex(int index) {
        return new EqualityGroup(
            entryId.apply(arrivedEntries.get(index)),
            convertedEntries[index], index, thresholdDistancePct);
    }

    public List<ListenableFuture<ENTRY>> getFutures() {
        return futures;
    }

    public List<? extends AbstractConsole> getConsoles() {
        return consoles;
    }

    public List<ENTRY> getArrivedEntries() {
        return arrivedEntries;
    }

    public List<ENTRY> getEntries() {
        return entries;
    }
}
