package bear.main.event;

import org.apache.logging.log4j.Level;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class LogEventToUI extends TextConsoleEventToUI {
    public LogEventToUI(String console, String message) {
        super(console, "log");
        this.textAdded = message;
    }

    public LogEventToUI(String console, String message, Level level) {
        super(console, "log");
        this.textAdded = message;
        this.level = level.intLevel();
    }

    public LogEventToUI(String message, int level) {
        super(Thread.currentThread().getName(), "log");
        this.textAdded = message;
        this.level = level;
    }
}
