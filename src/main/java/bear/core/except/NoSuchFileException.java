package bear.core.except;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class NoSuchFileException extends ValidationException{
    public NoSuchFileException(String message) {
        super(message);
    }

    public static void check(String script, String output) {
        ValidationException.checkLine("No such file or directory", script, output, NoSuchFileException.class);
    }
}
