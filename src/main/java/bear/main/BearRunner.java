package bear.main;

import bear.console.CompositeConsoleArrival;
import bear.core.*;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class BearRunner {
    private BearCommandLineConfigurator configurator;
    private IBearSettings bearSettings;
    private GlobalContextFactory factory;
    private GlobalContext global;
    @Deprecated
    private Script script;
    private String bearScript;
    private boolean shutdownAfterRun;
    private CompositeTaskRunContext runContext;
    private boolean await = true;

    public BearRunner(IBearSettings bearSettings, Script script, GlobalContextFactory factory) {
        try {
            this.bearSettings = bearSettings;
            this.factory = factory;
            this.script = script;
            this.global = bearSettings.getGlobal();

            init();
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public BearRunner(BearCommandLineConfigurator configurator, String bearScript) {
        this.configurator = configurator;
        this.bearScript = bearScript;
        this.global = configurator.getGlobal();

        try {
            this.bearSettings = configurator.newSettings();

            factory = configurator.getFactory();

            init();
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public BearRunner(BearCommandLineConfigurator configurator) {
        try {
            this.bearSettings = configurator.newSettings();
            this.script = (Script) configurator.getScriptToRun().get().aClass.newInstance();

            factory = configurator.getFactory();

            init();
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public final BearRunner init() throws Exception {
        bearSettings.configure(factory);
        if(script != null){
            script.setProperties(bearSettings.getGlobal(), null);
        }
        return this;
    }

    public CompositeTaskRunContext prepareToRun()  {
        try {
            Preconditions.checkArgument(bearSettings.isConfigured(), "settings must be configured. call settings.init() to configure");

            runContext = script.prepareToRun();

            return runContext;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public CompositeTaskRunContext run() throws Exception {
        runContext.submitTasks();

        if(shutdownAfterRun){
            if (await) {
                GlobalContext global = runContext.getGlobal();
                CompositeConsoleArrival<SessionContext> consoleArrival = runContext.getConsoleArrival();

                consoleArrival.await(global.localCtx.var(global.bear.taskTimeoutSec));
            }

            global.shutdown();
        }

        return runContext;
    }


    public BearRunner shutdownAfterRun(boolean shutdownAfterRun) {
        this.shutdownAfterRun = shutdownAfterRun;
        return this;
    }
}
