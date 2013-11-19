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
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    List<PhaseParty<C>> parties;
    List<Phase<?, PHASE>> phases ;

    final ArrayTable<PHASE, C, GridCell<C, ?>> table;

    private final Map<C, Integer> columnKeyToIndex;
    private final Map<PHASE, Integer> rowKeyToIndex;


    public ComputingGrid(Iterable<? extends Phase<?, PHASE>> phases, Iterable<? extends C> columnKeys) {
        table = ArrayTable.create(
            Iterables.transform(phases, Functions2.<Object, PHASE>field("phase")),
            columnKeys);

        partiesCount = table.columnKeyList().size();

        columnKeyToIndex = (Map<C, Integer>) OpenBean.getFieldValue(table, "columnKeyToIndex");
        rowKeyToIndex = (Map<PHASE, Integer>) OpenBean.getFieldValue(table, "rowKeyToIndex");

        this.parties = new ArrayList<PhaseParty<C>>(partiesCount);

        int i = 0;

        for (C columnKey : columnKeys) {
            this.parties.add(new PhaseParty<C>(i, columnKey, this));
            i++;
        }

        phases = Lists.newArrayList(phases);

        // this is needed because table is not synced
        // and cells may be referenced from future
        for (Phase<?, PHASE> phase : phases) {
            for (C columnKey : columnKeys) {
                table.put(phase.phase, columnKey, new GridCell());
            }

            addPhase(phase);
        }
    }

    public <V> List<ListenableFuture<V>> phaseFutures(Phase<V, PHASE> phase) {
        return phaseFutures(phase.rowIndex, null);
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

    public <V> GridCell<C, ?> cell(Integer rowKey, C columnKey) {
        return table.get(rowKey, columnKey);
    }

    public <V> GridCell<C, V> cell(Phase<V, PHASE> phase, C columnKey) {
        return (GridCell<C, V>) table.at(phase.getRowIndex(), partyToColumnIndex(columnKey));
    }

    public GridCell cellAt(int rowIndex, int columnIndex) {
        return table.at(rowIndex, columnIndex);
    }

    public <V> GridCell<C, V> cellAt(int rowIndex, int columnIndex, Class<V> vClass) {
        return (GridCell<C, V>) table.at(rowIndex, columnIndex);
    }

    public ComputingGrid<C, PHASE> startParties(ExecutorService service) {
        for (int i = 0; i < partiesCount; i++) {
            final int partyIndex = i;

            final PhaseParty<C> party = parties.get(i);

            service.submit(new CatchyRunnable(new Runnable() {
                @Override
                public void run() {
                    for(; party.currentPhaseIndex < phases.size();party.currentPhaseIndex++){
                        Phase<?, PHASE> phase = phases.get(party.currentPhaseIndex);
                        GridCell cell = table.at(party.currentPhaseIndex, partyIndex)
                            .started();

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
                        }
                    }
                }
            }));
        }

        return this;
    }

    public <V> ComputingGrid<C, PHASE> addPhase(Phase<V, PHASE> phase) {
        ImmutableList<C> list = table.columnKeyList();
        int rowIndex = phases.size();

        List<? extends PhaseCallable<C, V>> phaseCallables =  (List)phase.getParties(this);

        phase.rowIndex = rowIndex;

        for (int i = 0; i < list.size(); i++) {
            table.at(rowIndex, i).callable = (PhaseCallable) phaseCallables.get(i);
        }

        phases.add(phase);

        return this;
    }

    public <V> SettableFuture<V> previousResult(PhaseParty<C> party, int phaseIndex, Class<V> aClass) {
        return (SettableFuture<V>) table.at(phaseIndex - 1, partyToColumnIndex(party.column)).getFuture();
    }
}
