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

    private final GlobalContext global = GlobalContext.getInstance();

    public GlobalContextFactory() {
        global.loadProperties(global.localCtx.var(global.cap.settingsFile));
    }

    public void init() {
        if(globalVarsInitPhase != null){
            globalVarsInitPhase.setVars(global.variables);
        }

        if(registerPluginsPhase != null){
            final List<Class<? extends Plugin>> list = registerPluginsPhase.registerPlugins(global.variables);

            for (Class<? extends Plugin> aClass : list) {
                try {
                    final Plugin plugin = aClass.getConstructor(GlobalContext.class).newInstance(global);
                    Plugin.nameVars(plugin);
                    plugin.init();

                    global.pluginMap.put(aClass, plugin);
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
        configure(global, system);
        return global;
    }

    protected  GlobalContext configure(GlobalContext gc, SystemEnvironments system){
        logger.warn("warning: global config was not configured");
        return gc;
    }

    public GlobalContext getGlobal() {
        return global;
    }
}
