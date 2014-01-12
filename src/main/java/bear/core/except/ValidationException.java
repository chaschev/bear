package bear.core.except;

import bear.task.BearException;
import chaschev.lang.OpenBean;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ValidationException extends BearException {
    public ValidationException() {
    }

    public ValidationException(String message) {
        super(message);
    }

    public static ValidationException forScript(String name, String script, String output){
        return new ValidationException(name + " (" + script + ", " + output + ")");
    }

    public static void checkLine(String name, String script, String output, Class<? extends ValidationException> exceptionClass){
        if(output.contains(name)){
            throw OpenBean.newInstance(exceptionClass, name + " (" + script + ", " + output + ")");
        }
    }
}
