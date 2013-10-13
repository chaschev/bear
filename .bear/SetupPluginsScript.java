import bear.main.Script;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class SetupPluginsScript extends Script {
    @Override
    protected void configure() throws Exception {
        bear.stage.defaultTo("two");
        bear.autoInstallPlugins.defaultTo(true);
        bear.task.defaultTo(global.tasks.setup);
    }
}
