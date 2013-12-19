package bear.plugins.sh;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface ResultParser<T>{
    T parse(String script, String commandOutput);
}
