package bear.plugins;

import bear.core.SessionContext;
import bear.session.DynamicVariable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class HavingContext<CHILD> {
    protected SessionContext $;

    public HavingContext(SessionContext $) {
        this.$ = $;
    }

    public <T> T $(DynamicVariable<T> varName) {
        return $.var(varName);
    }

    public CHILD set$(SessionContext $) {
        this.$ = $;
        return (CHILD) this;
    }

    public SessionContext $() {
        return $;
    }
}
