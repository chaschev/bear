package bear.main.phaser;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PhaseParty<COL, PHASE> {
    final int index;
    final COL column;
    int currentPhaseIndex;

    PartyState state = PartyState.WAITING;

    GridException exception;
    long finishedAtMs;

    public final ComputingGrid<COL, PHASE> grid;

    public PhaseParty(int index, COL column, ComputingGrid<COL, PHASE> grid) {
        this.index = index;
        this.column = column;
        this.grid = grid;
    }

    public Exception getException() {
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

    public String getName(Object phase) {
        return "(" + phase + ", " + index + ")";
    }

    public COL getColumn() {
        return column;
    }

    public void fail() {
        throw new UnsupportedOperationException("todo PhaseParty.fail");
    }
}
