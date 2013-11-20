package bear.main.phaser;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface PhaseCallable<COL, V, PHASE> {
    V call(PhaseParty<COL, PHASE> party, int phaseIndex, Phase<?, PHASE> phase) throws Exception;
}
