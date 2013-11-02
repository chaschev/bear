package bear.main.event;

import bear.console.CompositeConsoleArrival;

import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class AllTasksFinishedEventToUI extends EventToUI {
    public long duration;
    public final List<CompositeConsoleArrival.EqualityGroup> groups;

    public AllTasksFinishedEventToUI(long duration, List<CompositeConsoleArrival.EqualityGroup> groups) {
        super("allFinished", "allFinished");
        this.duration = duration;
        this.groups = groups;
    }
}
