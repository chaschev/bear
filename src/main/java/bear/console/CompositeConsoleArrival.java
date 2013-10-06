package bear.console;

import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

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
    Future<EqualityGroups> groups;

    ENTRY[] entries;

    public CompositeConsoleArrival(List<ListenableFuture<ENTRY>> futures, List<? extends AbstractConsole> consoles, ListeningExecutorService executorService) {
        this.futures = futures;
        this.consoles = consoles;

        for (int i = 0; i < futures.size(); i++) {

        }
    }


    public void addArrival(int i, ENTRY entry) {
        throw new UnsupportedOperationException("todo CompositeConsoleArrival.addArrival");
    }

    public static class ArrivalEntry {
        CommandLineResult result;

        public ArrivalEntry(CommandLineResult result) {
            this.result = result;
        }
    }

    public static class EqualityGroups{
        List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

        int size;

        public EqualityGroups(List<EqualityGroup> groups) {
            this.groups = groups;

            for (EqualityGroup group : groups) {
                size += group.size();
            }
        }

        public Optional<EqualityGroup> getMajorityGroup(){
            return Iterables.tryFind(groups, new Predicate<EqualityGroup>() {
                @Override
                public boolean apply(EqualityGroup input) {
                    return input.size() > size / 2;
                }
            });
        }

        public ArrayList<EqualityGroup> getMinorGroups(){
            return newArrayList(filter(groups, not(equalTo(getMajorityGroup().orNull()))));
        }
    }

    public static class EqualityGroup{
        String text;
        int firstEntry;
        List<Integer> entries = new ArrayList<Integer>();

        private EqualityGroup(String text, int firstEntry) {
            this.text = text;
            this.firstEntry = firstEntry;
        }

        public boolean sameGroup(ArrivalEntry entry) {
            String otherText = entry.result.text;

            return getLevenshteinDistance(text, otherText, 5000) * 1.0 /
                (text.length() + otherText.length()) < 5;
        }

        public void add(ArrivalEntry entry, int i) {
            throw new UnsupportedOperationException("todo EqualityGroup.add");
        }

        public int size() {
            return entries.size();
        }
    }

    protected int thresholdDistance;

    protected List<EqualityGroup> divideIntoGroups(){
        List<EqualityGroup> groups = new ArrayList<EqualityGroup>();

//            groups.add(new EqualityGroup(entries[0].result.text, 0));
//
//            for (int i = 1; i < entries.length; i++) {
//                ArrivalEntry entry = entries[i];
//
//                for (EqualityGroup group : groups) {
//                    if(group.sameGroup(entry)){
//                        group.add(entry, i);
//                    }
//                }
//            }

        return groups;
    }

    public List<ListenableFuture<ENTRY>> getFutures() {
        return futures;
    }

    public List<? extends AbstractConsole> getConsoles() {
        return consoles;
    }
}
