package bear.console;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ConsoleCallbackResult {
    public final ConsoleCallbackResultType type;
    public final Object object;

    public ConsoleCallbackResult(ConsoleCallbackResultType type, Object object) {
        this.type = type;
        this.object = object;
    }

    public static final ConsoleCallbackResult CONTINUE = new ConsoleCallbackResult(ConsoleCallbackResultType.CONTINUE, null);
}
