package bear.task;

import bear.main.phaser.PhaseParty;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PartyResultException extends RuntimeException{
    TaskResult result;

    public PartyResultException(TaskResult result, PhaseParty party, Object phase) {
        super("bad result for party " + party.getName(phase) +": " + result.toString());
        this.result = result;
    }
}
