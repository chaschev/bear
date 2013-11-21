package bear.task;

import bear.main.phaser.PhaseParty;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PartyResultException extends RuntimeException{
    TaskResult result;

    private PartyResultException(TaskResult result, PhaseParty party, Object phase) {
        super(message(result, party, phase));

        this.result = result;
    }

    private static String message(TaskResult result, PhaseParty party, Object phase) {
        return "bad result for party " + party.getName(phase) + ": " + result.toString();
    }

    public PartyResultException(String message, Throwable cause) {
        super(message, cause);
    }

    public static PartyResultException create(TaskResult result, PhaseParty party, Object phase){
        if(result.exception.isPresent()){
            return new PartyResultException(message(result, party, phase), result.exception.get());
        }else{
            return new PartyResultException(result, party, phase);
        }
    }
}
