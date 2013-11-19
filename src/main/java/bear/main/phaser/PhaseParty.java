package bear.main.phaser;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PhaseParty<COL> {
    final int index;
    final COL column;
    int currentPhaseIndex;

    PartyState state = PartyState.WAITING;

    GridException exception;
    long finishedAtMs;

    public final ComputingGrid<COL, ?> grid;

    public PhaseParty(int index, COL column, ComputingGrid<COL, ?> grid) {
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

        for (int row = e.phase.rowIndex; row < grid.phases.size(); row++) {
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
