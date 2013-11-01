package bear.main.event;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TaskConsoleEventToUI extends ConsoleEventToUI {
    public String id;
    public String task;

    public TaskConsoleEventToUI(String console, String task) {
        super(console, "task");
        this.task = task;
    }

    public TaskConsoleEventToUI setId(String id) {
        this.id = id;
        return this;
    }
}
