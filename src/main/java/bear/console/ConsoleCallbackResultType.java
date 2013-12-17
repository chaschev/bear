package bear.console;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public enum ConsoleCallbackResultType {
    CONTINUE, DONE, EXCEPTION, WARNING, FINISHED;

    public boolean isError(){
        return this == EXCEPTION;
    }
}
