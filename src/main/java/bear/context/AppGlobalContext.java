package bear.context;

import bear.context.inject.Injectors;
import bear.core.BearApp;
import bear.session.DynamicVariable;

import java.lang.reflect.Field;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class AppGlobalContext<GLOBAL extends AppGlobalContext, BEAR_APP extends BearApp<GLOBAL>> extends AbstractContext {
    public final VariableRegistry variableRegistry = new VariableRegistry(this);

    public final BEAR_APP bear;
    protected final Injectors injectors = new Injectors();

    public AppGlobalContext(BEAR_APP bear) {
        super(new VariablesLayer("global layer", null));

        this.bear = bear;
        bear.setGlobal((GLOBAL) this);
    }

    public void registerVariable(DynamicVariable var, Field field) {
        variableRegistry.register(var, field);
    }

    @Override
    public AppGlobalContext getGlobal() {
        return this;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    public Injectors getInjectors() {
        return injectors;
    }
}
