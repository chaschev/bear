package bear.main.event;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class NewSessionConsoleEventToUI extends ConsoleEventToUI {
    public String id;

    public NewSessionConsoleEventToUI(String console, String id) {
        super(console, "session");
        this.id = id;
    }
}
