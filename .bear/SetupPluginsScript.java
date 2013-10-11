import bear.core.GlobalContextFactory;
import bear.main.BearRunner;
import bear.main.Script;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class SetupPluginsScript extends Script {
    @Override
    protected void configure() throws Exception {
        bear.stage.defaultTo("vm02");
        bear.autoInstallPlugins.defaultTo(true);
        bear.task.defaultTo(global.tasks.setup);
    }

    public static void main(String[] args) throws Exception {
        new BearRunner(
            new SetupPluginsSettings(GlobalContextFactory.INSTANCE, "/test.properties")
        , new SetupPluginsScript(), GlobalContextFactory.INSTANCE)
            .shutdownAfterRun(true)
            .prepareToRun();
    }
}
