package bear.main;

import bear.console.CompositeConsoleArrival;
import bear.core.*;
import com.google.common.base.Preconditions;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class BearRunner {
    private IBearSettings bearSettings;
    private GlobalContextFactory factory;
    private Script script;
    private boolean shutdownAfterRun;
    private CompositeTaskRunContext runContext;
    private boolean await = true;

    public BearRunner(IBearSettings bearSettings, Script script, GlobalContextFactory factory) throws Exception {
        this.bearSettings = bearSettings;
        this.factory = factory;
        this.script = script;

        init();
    }

    public BearRunner(BearCommandLineConfigurator configurator) throws Exception{
        this.bearSettings = configurator.newSettings();
        this.script = (Script) configurator.getScriptToRun().get().aClass.newInstance();

        factory = configurator.getFactory();

        init();
    }

    public final BearRunner init() throws Exception {
        bearSettings.configure(factory);
        script.setProperties(bearSettings.getGlobal(), null);
        return this;
    }

    public CompositeTaskRunContext prepareToRun() throws Exception {
        Preconditions.checkArgument(bearSettings.isConfigured(), "settings must be configured. call settings.init() to configure");

        runContext = script.prepareToRun();

        return runContext;
    }

    public CompositeTaskRunContext run() throws Exception {
        runContext.submitTasks();

        if(shutdownAfterRun){
            if (await) {
                GlobalContext global = runContext.getGlobal();
                CompositeConsoleArrival<SessionContext> consoleArrival = runContext.getConsoleArrival();

                consoleArrival.await(global.localCtx.var(global.bear.taskTimeoutSec));
            }

            script.global.shutdown();
        }

        return runContext;
    }


    public BearRunner shutdownAfterRun(boolean shutdownAfterRun) {
        this.shutdownAfterRun = shutdownAfterRun;
        return this;
    }
}
