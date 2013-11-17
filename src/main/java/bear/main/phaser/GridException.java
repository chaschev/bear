package bear.main.phaser;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GridException extends RuntimeException {
    final Phase<?> phase;
    final PhaseParty<?> party;

    public GridException(Throwable cause, Phase<?> phase, PhaseParty<?> party) {
        super("exception in cell (" + phase + ", " + party.column + ")", cause);

        this.phase = phase;
        this.party = party;
        party.exception = this;
    }
}
