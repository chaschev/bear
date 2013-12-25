package bear.task;

import bear.main.phaser.GridException;
import bear.main.phaser.Phase;
import bear.main.phaser.PhaseParty;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PartyResultException extends GridException{
    TaskResult result;

    private PartyResultException(TaskResult result, PhaseParty<?, ?> party, Phase<?, ?> phase) {
        super(message(result, party, phase), phase, party);

        this.result = result;
    }

    private static String message(TaskResult result, PhaseParty party, Object phase) {
        return "bad result for cell " + party.getName(phase) + ": " + result.toString();
    }

    public PartyResultException(Throwable cause, Phase<?, ?> phase, PhaseParty<?, ?> party, TaskResult result) {
        super(cause, phase, party);
        this.result = result;
    }

    public static PartyResultException create(TaskResult result, PhaseParty<?, ?> party, Phase<?, ?> phase){
        if(result.exception.isPresent()){
            return new PartyResultException(result.exception.get(), phase, party, result);
        }else{
            return new PartyResultException(result, party, phase);
        }
    }
}
