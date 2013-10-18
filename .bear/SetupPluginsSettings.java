import atocha.Atocha;
import bear.core.*;
import bear.plugins.grails.GrailsBuilderTask;
import bear.plugins.grails.GrailsPlugin;
import bear.plugins.java.JavaPlugin;
import bear.plugins.tomcat.MavenPlugin;
import bear.plugins.tomcat.TomcatPlugin;
import bear.strategy.DeployStrategyTask;
import bear.strategy.SymlinkEntry;
import bear.task.TaskResult;
import bear.vcs.GitCLIPlugin;

import static bear.session.GenericUnixRemoteEnvironment.newUnixRemote;

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

    SetupPluginsSettings(GlobalContextFactory factory, String resource) {
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

        bear.stages.defaultTo(
            new Stages()
                .add(
                    new Stage("vm01", global)
                        .add(newUnixRemote("vm01", "vm01", global))
                )
                .add(
                    new Stage("two", global)
                        .add(newUnixRemote("vm01", "vm01", global))
                        .add(newUnixRemote("vm02", "vm02", global))
                )
                .add(new Stage("three", global)
                    .add(newUnixRemote("vm01", "vm01", global))
                    .add(newUnixRemote("vm02", "vm02", global))
                    .add(newUnixRemote("vm03", "vm03", global))
                )
        );

        bear.getStrategy.setDynamic(new VarFun<DeployStrategyTask>() {

            public DeployStrategyTask apply() {

                grails.projectPath.setEqualTo(
                    bear.vcsBranchLocalPath
                );


                //todo create a builder for it
                //todo return task result for each of the steps
                //todo convert each step to a task? - yes! this will make steps reusable
                final DeployStrategyTask strategy = new DeployStrategyTask($) {
                    //todo return TaskResult
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        $.runner.run(global.tasks.vcsUpdate);

                        $.log("building the project...");

                        String warPath = $(grails.releaseWarPath);

                        if (!$.sys.exists(warPath) || !$(global.getPlugin(Atocha.class).reuseWar)) {
                            final TaskResult r = $.runner.run(new GrailsBuilderTask(global));

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
                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, global.var(bear.appUsername) + "." + global.var(bear.appUsername))
                );

                return strategy;
            }
        });

        return global;
    }
}
