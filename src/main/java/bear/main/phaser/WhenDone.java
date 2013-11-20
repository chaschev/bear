package bear.main.phaser;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface WhenDone<COL, V, PHASE> {
    void act(V result, PhaseParty<COL, PHASE> party);
}
