package bear.main;

import bear.task.TaskResult;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class RootTaskFinishedEventToUI extends ConsoleEventToUI {
    public TaskResult result;
    public long duration;


    public RootTaskFinishedEventToUI(TaskResult result, long duration, String console) {
        super(console, "rootTaskFinished");
        this.result = result;
        this.duration = duration;
    }
}
