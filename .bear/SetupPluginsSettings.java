import atocha.Atocha;
import bear.context.Fun;
import bear.core.*;
import bear.plugins.grails.GrailsBuilderTask;
import bear.plugins.grails.GrailsPlugin;
import bear.plugins.java.JavaPlugin;
import bear.plugins.maven.MavenPlugin;
import bear.plugins.tomcat.TomcatPlugin;
import bear.strategy.DeployStrategyTaskDef;
import bear.strategy.SymlinkEntry;
import bear.task.TaskResult;
import bear.vcs.GitCLIPlugin;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SetupPluginsSettings extends IBearSettings {
    GrailsPlugin grails;
    JavaPlugin java;
    MavenPlugin maven;
    Bear bear;
    TomcatPlugin tomcat;
    GlobalContext global;
    GitCLIPlugin git;

    public SetupPluginsSettings(GlobalContextFactory factory) {
        super(factory);
    }

    public SetupPluginsSettings(GlobalContextFactory factory, String resource) {
        super(factory, resource);
    }

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
        factory.init(this);

        tomcat.warName.setEqualTo(grails.warName);
        tomcat.version.set("6.0.37");

        maven.version.set("3.0.5");

        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        grails.version.set("2.0.4");

        Stages stages = new Stages(global);
        bear.stages.defaultTo(
            stages
                .add(
                    new Stage("one")
                    .addHosts(stages.hosts("vm01")))
                .add(
                    new Stage("two")
                        .addHosts(stages.hosts("vm01, vm02")))
                .add(
                    new Stage("three")
                        .addHosts(stages.hosts("vm01, vm02, vm03"))
                ))
        ;

        bear.getStrategy.setDynamic(new Fun<DeployStrategyTaskDef, SessionContext>() {

            public DeployStrategyTaskDef apply(final SessionContext $) {
                grails.projectPath.setEqualTo(bear.vcsBranchLocalPath);

                final DeployStrategyTaskDef strategy = new DeployStrategyTaskDef($) {
                    //todo return TaskResult
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        $.run(global.tasks.vcsUpdate);

                        $.log("building the project...");

                        String warPath = $.var(grails.releaseWarPath);

                        if (!$.sys.exists(warPath) || !$.var(global.getPlugin(Atocha.class).reuseWar)) {
                            final TaskResult r = $.run(new GrailsBuilderTask(global));

                            if (r.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }
                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(SessionContext localCtx) {

                    }
                };

                //todo: strategy.addSymlink, 2 DynVariables constructor
                strategy.getSymlinks().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, global.var(bear.appUsername) + "." + global.var(bear.appUsername))
                );

                return strategy;
            }
        });

        return global;
    }
}
