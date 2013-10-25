package bear.main.event;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ConsoleEventToUI extends EventToUI {
    public String console;

    public ConsoleEventToUI(String consoleName) {
        super("console");
        this.console = consoleName;
    }

    public ConsoleEventToUI(String consoleName, String subType) {
        super("console", subType);
        this.console = consoleName;
    }
}
