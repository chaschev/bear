package bear.main.event;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SessionConsoleEventToUI extends ConsoleEventToUI {
    public String id;

    public SessionConsoleEventToUI(String console, String id) {
        super(console, "session");
        this.id = id;
    }
}
