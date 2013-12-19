package bear.core.except;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WrongCommandException extends ValidationException{
    public WrongCommandException(String message) {
        super(message);
    }
}
