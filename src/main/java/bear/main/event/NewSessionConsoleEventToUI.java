package bear.main.event;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class NewSessionConsoleEventToUI extends ConsoleEventToUI {
    public final String id;
    public final String phaseId;

    public NewSessionConsoleEventToUI(String console, String id, String phaseId) {
        super(console, "session");
        this.id = id;
        this.phaseId = phaseId;
    }
}
