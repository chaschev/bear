package bear.main.event;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class LogEventToUI extends TextConsoleEventToUI {
    public final int level;

    public LogEventToUI(String console, String message, int level) {
        super(console, "log");
        this.textAdded = message;
        this.level = level;
    }

    public LogEventToUI(String message, int level) {
        super(Thread.currentThread().getName(), "log");
        this.textAdded = message;
        this.level = level;
    }
}
