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

    protected double thresholdDistancePct = 5;

    public CompositeConsoleArrival(
        List<ENTRY> entries, List<ListenableFuture<ENTRY>> futures, List<? extends AbstractConsole> consoles,
        Function<ENTRY, String> entryAsText) {

        this.entries = entries;

        this.futures = futures;
        this.consoles = consoles;
        this.entryAsText = entryAsText;

        arrivedEntries = LangUtils.newFilledArrayList(consoles.size(), null);
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
        List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

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

    public static class EqualityGroup<ENTRY> {
        String text;
        int firstEntry;
        private final double thresholdDistancePct;
        List<Integer> entries = new ArrayList<Integer>();

        private EqualityGroup(String text, int firstEntry, double thresholdDistancePct) {
            this.text = text;
            this.firstEntry = firstEntry;
            this.thresholdDistancePct = thresholdDistancePct;
        }

        public boolean sameGroup(String otherText) {

            return getLevenshteinDistance(text, otherText, 5000) * 1.0 /
                (text.length() + otherText.length()) < thresholdDistancePct;
        }

        public int size() {
            return entries.size();
        }

        public void add(int i) {
            entries.add(i);
        }
    }

    public List<EqualityGroup> divideIntoGroups() {
        for (int i = 0; i < futures.size(); i++) {
            convertedEntries[i] = entryAsText.apply(arrivedEntries.get(i));
        }

        List<EqualityGroup> groups = new ArrayList<EqualityGroup>();
        groups.add(new EqualityGroup(convertedEntries[0], 0, thresholdDistancePct));

        for (int i = 1; i < convertedEntries.length; i++) {
            String entryText = convertedEntries[i];

            for (EqualityGroup group : groups) {
                if (group.sameGroup(entryText)) {
                    group.add(i);
                }
            }
        }

        return groups;
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
