package cap4j.core;

import cap4j.plugins.Plugin;
import cap4j.session.SystemEnvironments;
import com.chaschev.chutils.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
* User: ACHASCHEV
* Date: 7/23/13
*/
public class GlobalContextFactory {
    private static final Logger logger = LoggerFactory.getLogger(GlobalContextFactory.class);

    public static GlobalContextFactory INSTANCE = new GlobalContextFactory();

    private final GlobalContext globalContext = GlobalContext.getInstance();


    public GlobalContextFactory() {
    }

    public void init() {
        if(globalVarsInitPhase != null){
            globalVarsInitPhase.setVars(globalContext.variables);
        }

        if(registerPluginsPhase != null){
            final List<Class<? extends Plugin>> list = registerPluginsPhase.registerPlugins(globalContext.variables);

            for (Class<? extends Plugin> aClass : list) {
                try {
                    final Plugin plugin = aClass.getConstructor(GlobalContext.class).newInstance(globalContext);
                    Plugin.nameVars(plugin);
                    plugin.init();

                    globalContext.pluginMap.put(aClass, plugin);
                } catch (Exception e) {
                    throw Exceptions.runtime(e);
                }
            }
        }
    }

    public static interface GlobalVarsInitPhase {
        void setVars(Variables vars);
    }

    public static interface RegisterPluginsPhase {
        List<Class<? extends Plugin>> registerPlugins(Variables vars);
    }

    public GlobalVarsInitPhase globalVarsInitPhase;
    public RegisterPluginsPhase registerPluginsPhase;

    public GlobalContext configure(SystemEnvironments system){
        configure(globalContext, system);
        return globalContext;
    }

    protected  GlobalContext configure(GlobalContext gc, SystemEnvironments system){
        logger.warn("warning: global config was not configured");
        return gc;
    }

    public GlobalContext getGlobalContext() {
        return globalContext;
    }
}
