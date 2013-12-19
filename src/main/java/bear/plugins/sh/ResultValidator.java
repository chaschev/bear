package bear.plugins.sh;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface ResultValidator{
    void validate(String script, String output);
}
