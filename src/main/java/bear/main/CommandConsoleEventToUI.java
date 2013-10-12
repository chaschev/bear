package bear.main;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class CommandConsoleEventToUI extends ConsoleEventToUI {
    public String command;

    public CommandConsoleEventToUI(String console, String command) {
        super(console, "command");
        this.command = command;
    }
}
