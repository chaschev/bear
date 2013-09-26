package cap4j.plugins;

import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.session.DynamicVariable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class SessionPlugin extends Plugin{
    protected SessionContext $;

    public SessionPlugin(GlobalContext global) {
        super(global);
    }

    public <T> T $(DynamicVariable<T> varName) {
        return $.var(varName);
    }

    public Plugin set$(SessionContext $) {
        this.$ = $;
        return this;
    }
}
