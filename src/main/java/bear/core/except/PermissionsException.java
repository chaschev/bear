package bear.core.except;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class PermissionsException extends ValidationException {
    public PermissionsException(String message) {
        super(message);
    }

    public static void check(String script, String output) {
        ValidationException.checkLine("Permission denied", script, output, PermissionsException.class);
    }
}
