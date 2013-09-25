package cap4j.core;

import java.io.InputStream;

/**
 * User: achaschev
 * Date: 8/12/13
 */
public abstract class ICapSettings {
    protected GlobalContextFactory factory;
    protected GlobalContext global;

    protected ICapSettings(GlobalContextFactory factory) {
        this.factory = factory;
        global = factory.getGlobal();
    }

    public final GlobalContext configure(GlobalContextFactory factory) throws Exception {
        configureMe(factory);

        return global;
    }

    protected abstract GlobalContext configureMe(GlobalContextFactory factory) throws Exception;

    public ICapSettings loadProperties(InputStream is) throws Exception {
        global.loadProperties(is);

        return this;
    }

    public GlobalContextFactory getFactory() {
        return factory;
    }

    public GlobalContext getGlobal() {
        return global;
    }
}
