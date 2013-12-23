package bear.core.except;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WrongCommandException extends ValidationException{
    public WrongCommandException(String message) {
        super(message);
    }

    public static void check(String script, String output) {
        ValidationException.checkLine("bash: command not found", script, output, WrongCommandException.class);
    }
}
