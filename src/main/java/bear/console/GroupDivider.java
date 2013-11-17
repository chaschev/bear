package bear.console;

import chaschev.lang.Lists2;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static chaschev.lang.Predicates2.functionAppliedEquals;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.indexOf;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.getLevenshteinDistance;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GroupDivider<ENTRY> {
    public static class ArrivedEntry<ENTRY> {
        public String entryId;
        public ENTRY entry;

        public ArrivedEntry(String entryId, ENTRY entry) {
            this.entryId = entryId;
            this.entry = entry;
        }
    }

    protected List<ENTRY> entries;
    protected List<ArrivedEntry<ENTRY>> arrivedEntries = new ArrayList<ArrivedEntry<ENTRY>>();

    protected String[] convertedEntries;

    protected Function<ENTRY, String> entryAsText;
    protected Function<ENTRY, String> entryId;     // i.e. taskId
    protected Function<ENTRY, String> groupById;   // i.e. sessionId, used to find index in an entries array

    protected double thresholdDistancePct = 5;

    public GroupDivider(List<ENTRY> entries, Function<ENTRY, String> groupById, Function<ENTRY, String> entryId, Function<ENTRY, String> entryAsText) {
        this.entries = entries;
        this.groupById = groupById;
        convertedEntries = new String[entries.size()];
        arrivedEntries = Lists2.newFilledArrayList(entries.size(), null);
        this.entryId = entryId;
        this.entryAsText = entryAsText;
    }

    public GroupDivider(List<ENTRY> entries, Function<ENTRY, String> entryId, Function<ENTRY, String> entryAsText) {
        this(entries, entryId, entryId, entryAsText);
    }

    public void addArrival(int i, ENTRY entry) {
        arrivedEntries.set(i, new ArrivedEntry<ENTRY>(entryId.apply(entry), entry));

        if(entry == null){
            convertedEntries[i] = null;
        } else{
            convertedEntries[i] = entryAsText.apply(entry);
        }
    }

    public void addArrival(ENTRY entry) {
        int i = indexOf(entries, functionAppliedEquals(groupById, groupById.apply(entry)));
        addArrival(i, entry);
    }

    public List<EqualityGroup> divideIntoGroups() {
        List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

        EqualityGroup nullGroup = null;

        for (int i = 0; i < arrivedEntries.size(); i++) {
            ArrivedEntry<ENTRY> arrivedEntry = arrivedEntries.get(i);
            ENTRY entry = arrivedEntry.entry;

            if (entry == null) {
                convertedEntries[i] = null;

                if (nullGroup == null) {
                    nullGroup = newGroupByIndex(i);
                    nullGroup.text = null;
                } else {
                    nullGroup.add(arrivedEntry.entryId);
                }
            }
        }


        int nonNullIndex = indexOf(asList(convertedEntries), Predicates.notNull());

        if (nonNullIndex == -1) {
            if (nullGroup == null) {
                return new ArrayList<EqualityGroup>();
            }

            return Lists.newArrayList(nullGroup);
        }
        groups.add(newGroupByIndex(nonNullIndex));

        for (int i = 1; i < convertedEntries.length; i++) {
            String entryText = convertedEntries[i];

            if (entryText == null) {
                continue;
            }

            boolean foundGroup = false;
            double distance = 0;

            for (EqualityGroup group : groups) {
                distance = group.distancePct(entryText);
                if (group.sameGroup(distance)) {
                    group.add(arrivedEntries.get(i).entryId);
                    foundGroup = true;
                    break;
                }
            }

            if (!foundGroup) {
                EqualityGroup group = newGroupByIndex(i);
                groups.add(group);
                group.distance = (int) distance;
            }
        }

//        EqualityGroups equalityGroups = new EqualityGroups(groups);

        Collections.sort(groups);

        if (nullGroup != null) {
            groups.add(nullGroup);
        }

        return groups;
    }

    private EqualityGroup newGroupByIndex(int index) {
        ArrivedEntry<ENTRY> arrivedEntry = arrivedEntries.get(index);
        ENTRY input = arrivedEntry.entry;

        return new EqualityGroup(
            input == null ? null : arrivedEntry.entryId,
            convertedEntries[index], index, thresholdDistancePct);
    }

    public List<ENTRY> getEntries() {
        return entries;
    }

    public static class EqualityGroups {
        public List<CompositeConsoleArrival.EqualityGroup> groups = new ArrayList<CompositeConsoleArrival.EqualityGroup>();

        int size;

        public EqualityGroups(List<CompositeConsoleArrival.EqualityGroup> groups) {
            this.groups = groups;

            for (EqualityGroup group : groups) {
                size += group.size();
            }
        }

        public Optional<CompositeConsoleArrival.EqualityGroup> getMajorityGroup() {
            return Iterables.tryFind(groups, new Predicate<CompositeConsoleArrival.EqualityGroup>() {
                @Override
                public boolean apply(EqualityGroup input) {
                    return input.size() > size / 2;
                }
            });
        }

        public ArrayList<CompositeConsoleArrival.EqualityGroup> getMinorGroups() {
            return newArrayList(filter(groups, not(equalTo(getMajorityGroup().orNull()))));
        }
    }

    public static class EqualityGroup<ENTRY> implements Comparable<CompositeConsoleArrival.EqualityGroup<?>> {
        public String id;
        public String text;
        public int firstEntry;
        public final double thresholdDistancePct;
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
        public int compareTo(EqualityGroup o) {
            return size() - o.size();
        }
    }
}
