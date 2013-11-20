package bear.main.phaser;

import chaschev.lang.Functions2;
import chaschev.lang.Lists2;
import chaschev.lang.OpenBean;
import chaschev.util.CatchyRunnable;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Elegant sweep strategy: add listener for
 *
 * Table can be improved to represent only a computation frame:
 *  - present a default sweep strategy, add exclusions
 *  - provide a phase iterator
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ComputingGrid<C, PHASE> {
    private final int partiesCount;

    //todo this should be extracted into a builder
//    private transient int builderPhaseCount;

//    List<Phase<?>> phases = new ArrayList<>();
    final List<PhaseParty<C, PHASE>> parties;
    final Collection<? extends Phase<?, PHASE>> phases ;

    final ArrayTable<PHASE, C, GridCell<C, ?, PHASE>> table;

    private final Map<C, Integer> columnKeyToIndex;
    private final Map<PHASE, Integer> rowKeyToIndex;


    public ComputingGrid(Collection<? extends Phase<?, PHASE>> phases, Iterable<? extends C> columnKeys) {
        this.phases = phases;
        table = ArrayTable.create(
            Iterables.transform(phases, Functions2.<Object, PHASE>field("phase")),
            columnKeys);

        partiesCount = table.columnKeyList().size();

        columnKeyToIndex = (Map<C, Integer>) OpenBean.getFieldValue(table, "columnKeyToIndex");
        rowKeyToIndex = (Map<PHASE, Integer>) OpenBean.getFieldValue(table, "rowKeyToIndex");

        this.parties = new ArrayList<PhaseParty<C, PHASE>>(partiesCount);

        int i = 0;

        for (C columnKey : columnKeys) {
            this.parties.add(new PhaseParty<C, PHASE>(i, columnKey, this));
            i++;
        }

        phaseEntered = new boolean[phases.size()];

        // can't load these lazily as futures are accessed from outside
        // will probably need to split out futures for lazy init or get the futures...
        for (Phase<?, PHASE> phase : phases) {
            addPhase(phase);
        }
    }

    public <V> List<ListenableFuture<V>> phaseFutures(Phase<V, PHASE> phase) {
        return phaseFutures(phaseToRowIndex(phase.phase), null);
    }

    public <V> List<ListenableFuture<V>> phaseFutures(final int rowIndex, Class<V> vClass) {
        return Lists2.computingList(partiesCount, new Function<Integer, ListenableFuture<V>>() {
            public ListenableFuture<V> apply(Integer colIndex) {
                return (ListenableFuture<V>) table.at(rowIndex, colIndex).getFuture();
            }
        });
    }

    public Integer phaseToRowIndex(PHASE phase) {
        return rowKeyToIndex.get(phase);
    }

    public Integer partyToColumnIndex(C col) {
        return columnKeyToIndex.get(col);
    }

    public <V> ListenableFuture<List<V>> aggregateSuccessful(Phase<V, PHASE> phase) {
        return Futures.successfulAsList(phaseFutures(phase));
    }


    public <V> ListenableFuture<List<V>> aggregatedPhase(int phase, Class<V> vClass) {
        return Futures.successfulAsList(phaseFutures(phase, vClass));
    }

    ComputingGrid<C, PHASE> awaitTermination(){
        try {

            aggregatedPhase(phaseToRowIndex(lastPhase()), Object.class).get();
            return this;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    private PHASE lastPhase() {
        ImmutableList<PHASE> row = table.rowKeyList();

        return row.get(row.size() - 1);
    }

    public <V> GridCell<C, ?, PHASE> cell(int rowKey, C columnKey) {
        return table.get(rowKey, columnKey);
    }

    public <V> GridCell<C, V, PHASE> cell(Phase<V, PHASE> phase, C columnKey) {
        return (GridCell<C, V, PHASE>) table.at(phaseToRowIndex(phase.phase), partyToColumnIndex(columnKey));
    }

    public GridCell cellAt(int rowIndex, int columnIndex) {
        return table.at(rowIndex, columnIndex);
    }

    public <V> GridCell<C, V, PHASE> cellAt(int rowIndex, int columnIndex, Class<V> vClass) {
        return (GridCell<C, V, PHASE>) table.at(rowIndex, columnIndex);
    }

    private final boolean[] phaseEntered;

    public static interface PhaseEnterListener<PHASE, C>{
        void enter(Phase<?, PHASE> phase, PhaseParty<C, PHASE> party);
    }

    protected PhaseEnterListener<PHASE, C> phaseEnterListener;

    private void checkPhaseEntered(Phase<?, PHASE> phase, PhaseParty<C, PHASE> party){
        if(phaseEnterListener == null || phaseEntered[phase.rowIndex]){
            return;
        }

        synchronized (phaseEntered){
            if(phaseEntered[phase.rowIndex]) return;

            phaseEntered[phase.rowIndex] = true;
        }

        phaseEnterListener.enter(phase, party);
    }

    public ComputingGrid<C, PHASE> startParties(ExecutorService service) {
        for (int i = 0; i < partiesCount; i++) {
            final int partyIndex = i;

            final PhaseParty<C, PHASE> party = parties.get(i);

            service.submit(new CatchyRunnable(new Runnable() {
                @Override
                public void run() {
                    for (Phase<?, PHASE> phase : phases) {
                        GridCell cell = table.at(party.currentPhaseIndex, partyIndex)
                            .started();

                        checkPhaseEntered(phase, party);

                        Object result = null;

                        try {
                            result = cell.callable.call(party, party.currentPhaseIndex, phase);

                            cell.getFuture().set(result);

                            if (cell.whenDone != null) {
                                cell.whenDone.act(result, party);
                            }
                        } catch (Exception e) {
                            GridException gridException = new GridException(e, phase, party);
                            party.setException(gridException);
                            LoggerFactory.getLogger("log").warn(e.toString(), gridException);

                            break;
                        } finally {
                            cell.finishedAtMs = System.currentTimeMillis();
                            party.currentPhaseIndex++;
                        }
                    }
                }
            }));
        }

        return this;
    }

    private  synchronized <V> void addPhase(Phase<V, PHASE> phase) {
        Integer rowIndex = phaseToRowIndex(phase.getPhase());
        if(rowIndex != null && table.at(rowIndex, 0) != null){
            return;
        }

        for (C columnKey : table.columnKeyList()) {
            table.put(phase.phase, columnKey, new GridCell());
        }

        ImmutableList<C> list = table.columnKeyList();

        List<? extends PhaseCallable<C, V, PHASE>> phaseCallables =  (List)phase.getParties(this);

        phase.rowIndex = rowIndex;

        for (int i = 0; i < list.size(); i++) {
            table.at(rowIndex, i).callable = (PhaseCallable) phaseCallables.get(i);
        }
    }

    public <V> SettableFuture<V> previousResult(PhaseParty<C, PHASE> party, int phaseIndex, Class<V> aClass) {
        return (SettableFuture<V>) table.at(phaseIndex - 1, partyToColumnIndex(party.column)).getFuture();
    }

    public ComputingGrid<C, PHASE> setPhaseEnterListener(PhaseEnterListener<PHASE, C> phaseEnterListener) {
        this.phaseEnterListener = phaseEnterListener;
        return this;
    }
}
