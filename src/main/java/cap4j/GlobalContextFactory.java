package cap4j;

import cap4j.session.SystemEnvironments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* User: ACHASCHEV
* Date: 7/23/13
*/
public class GlobalContextFactory {
    private static final Logger logger = LoggerFactory.getLogger(GlobalContextFactory.class);
    public GlobalContext create(SystemEnvironments system){
        GlobalContext globalContext = new GlobalContext() {

        };

        configure(globalContext, system);

        GlobalContext.INSTANCE = globalContext;

        return globalContext;
    }

    protected  GlobalContext configure(GlobalContext gc, SystemEnvironments system){
        logger.warn("warning: global config was not configured");
        return gc;
    }
}
