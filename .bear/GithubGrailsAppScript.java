import atocha.Atocha;
import bear.main.Script;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class GithubGrailsAppScript extends Script {
    @Override
    public void configure() throws Exception {
        bear.stage.defaultTo("vm02");
        bear.task.defaultTo(global.tasks.deploy);
        bear.clean.defaultTo(false, true);

        global.getPlugin(Atocha.class).reuseWar.defaultTo(true, true);

        bear.vcsBranchName.defaultTo("master");
    }
}
