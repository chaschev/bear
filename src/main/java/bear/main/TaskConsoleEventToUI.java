package bear.main;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TaskConsoleEventToUI extends ConsoleEventToUI {
    public String task;

    public TaskConsoleEventToUI(String console, String task) {
        super(console, "task");
        this.task = task;
    }
}
