package bear.main.phaser;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface PhaseCallable<COL, V> {
    V call(PhaseParty<COL> party, int phaseIndex) throws Exception;
}
