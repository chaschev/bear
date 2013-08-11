package cap4j.core;

import cap4j.session.SystemEnvironments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* User: ACHASCHEV
* Date: 7/23/13
*/
public class GlobalContextFactory {
    private static final Logger logger = LoggerFactory.getLogger(GlobalContextFactory.class);

    public static GlobalContextFactory INSTANCE = new GlobalContextFactory();


    public GlobalContextFactory() {
    }

    public void init() {
        GlobalContext globalContext = new GlobalContext();

        GlobalContext.INSTANCE = globalContext;

        if(globalVarsInitPhase != null){
            globalVarsInitPhase.setVars(globalContext.variables);
        }
    }

    public static interface GlobalVarsInitPhase {
        void setVars(Variables vars);
    }

    public GlobalVarsInitPhase globalVarsInitPhase;

    public GlobalContext configure(SystemEnvironments system){
        configure(GlobalContext.INSTANCE, system);
        return GlobalContext.INSTANCE;
    }

    protected  GlobalContext configure(GlobalContext gc, SystemEnvironments system){
        logger.warn("warning: global config was not configured");
        return gc;
    }
}
