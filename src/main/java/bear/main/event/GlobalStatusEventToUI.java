package bear.main.event;

import bear.core.CompositeTaskRunContext;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalStatusEventToUI extends EventToUI {
    public CompositeTaskRunContext.Stats stats;

    public GlobalStatusEventToUI(CompositeTaskRunContext.Stats stats) {
        super("status", "global");

        this.stats = stats;
    }
}
