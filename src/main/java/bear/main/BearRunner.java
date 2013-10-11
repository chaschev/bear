package bear.main;

import bear.core.CompositeTaskRunContext;
import bear.core.GlobalContextFactory;
import bear.core.IBearSettings;
import com.google.common.base.Preconditions;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class BearRunner {
    private IBearSettings bearSettings;
    private GlobalContextFactory factory;
    private Script script;

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

    public CompositeTaskRunContext run() throws Exception {
        Preconditions.checkArgument(bearSettings.isConfigured(), "settings must be configured. call settings.init() to configure");

        return script.run();
    }
}
