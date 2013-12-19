package bear.core.except;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class NoSuchFileException extends ValidationException{
    public NoSuchFileException(String message) {
        super(message);
    }
}
