package bear.plugins;

import bear.session.DynamicVariable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */


public class HavingContext<CHILD, CONTEXT extends AbstractContext> {
    protected CONTEXT $;

    public HavingContext(CONTEXT $) {
        this.$ = $;
    }

    public HavingContext(Object $) {
        this.$ = (CONTEXT) $;
    }

    public <T> T $(DynamicVariable<T> varName) {
        Object var = $.var(varName);
        return (T) var;
    }

    public CHILD set$(CONTEXT $) {
        this.$ = $;
        return (CHILD) this;
    }

    public CHILD set$(Object $) {
        this.$ = (CONTEXT) $;
        return (CHILD) this;
    }

    public CONTEXT $() {
        return $;
    }
}
