package bear.main.event;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class CommandConsoleEventToUI extends ConsoleEventToUI {
    public String id;
    public String command;

    public CommandConsoleEventToUI(String console, String command) {
        super(console, "command");
        this.command = command;
    }

    public CommandConsoleEventToUI setId(String id) {
        this.id = id;
        return this;
    }
}
