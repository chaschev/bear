package bear.main.phaser;

import chaschev.lang.OpenBean;
import chaschev.lang.reflect.MethodDesc;
import com.google.common.base.Optional;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PhaseParty<COL, PHASE> {
    final int index;
    final COL column;
    private final String name;
    int currentPhaseIndex;

    PartyState state = PartyState.WAITING;

    GridException exception;
    long finishedAtMs;
    Object lastResult;

    public final ComputingGrid<COL, PHASE> grid;

    public PhaseParty(int index, COL column, ComputingGrid<COL, PHASE> grid) {
        this.index = index;
        this.column = column;
        this.grid = grid;
        Optional<MethodDesc> nameMethod = OpenBean.getMethod(column, "getName");
        if(nameMethod.isPresent()){
            this.name = (String) nameMethod.get().invoke(column);
        }else{
            this.name = "" + index;
        }
    }

    public GridException getException() {
        return exception;
    }

    public long getFinishedAtMs() {
        return finishedAtMs;
    }

    public void setException(GridException e) {
        this.exception = e;
        this.state = PartyState.BROKEN;

        Integer rowKey = grid.phaseToRowIndex((PHASE) e.phase.getPhase());
        for (int row = rowKey; row < grid.phases.size(); row++) {
            grid.table.at(row, e.party.index).getFuture().setException(e);
        }
    }

    public String getName() {
        return name;
    }

    public String getName(Object phase) {
        return "(" + phase + ", " + name + ")";
    }

    public COL getColumn() {
        return column;
    }

    public void fail() {
        throw new UnsupportedOperationException("todo PhaseParty.fail");
    }

    public boolean failed(){
        return exception != null;
    }

    public int getIndex() {
        return index;
    }
}
