package bear.main;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class Response {
    public String getClazz() {
        return getClass().getSimpleName();
    }
}
