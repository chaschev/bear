package cap4j.smoke;

import atocha.Atocha;
import cap4j.core.*;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.MavenPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.vcs.GitCLIPlugin;
import cap4j.strategy.DeployStrategy;
import cap4j.strategy.SymlinkEntry;
import cap4j.task.TaskResult;
import com.google.common.collect.Lists;

import java.util.List;

import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class SetupPluginsSettings extends ICapSettings {
    GrailsPlugin grails;
    JavaPlugin java;
    Cap cap;
    TomcatPlugin tomcat;

    SetupPluginsSettings(GlobalContextFactory factory) {
        super(factory);
    }

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
        final GlobalContext global = factory.getGlobal();

        factory.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
            @Override
            public List<Class<? extends Plugin>> registerPlugins(VariablesLayer vars) {
                return Lists.newArrayList(
                    JavaPlugin.class,
                    MavenPlugin.class,
                    TomcatPlugin.class,
                    GrailsPlugin.class,
                    GitCLIPlugin.class
                    ,Atocha.class
                );
            }
        };

        factory.init();

        tomcat = global.getPlugin(TomcatPlugin.class);
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        cap = global.cap;

        tomcat.warName.setEqualTo(grails.warName);
        tomcat.version.defaultTo("6.0.37");

        java.versionName.defaultTo("jdk-7u40-linux-x64");
        java.version.defaultTo("1.7.0_40");

        grails.version.defaultTo("2.0.4", true);

        cap.stages.defaultTo(
            new Stages()
                .add(new Stage("vm02", global)
                    .add(newUnixRemote("vm01", "vm01", global)))
        );

        cap.getStrategy.setDynamic(new VarFun<DeployStrategy>() {

            public DeployStrategy apply() {

                grails.projectPath.setEqualTo(
                    cap.vcsBranchLocalPath
                );

                final DeployStrategy strategy = new DeployStrategy($) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        $.runner.run(global.tasks.vcsUpdate);

                        $.log("building the project...");

                        String warPath = $(grails.releaseWarPath);

                        if (!$.sys.exists(warPath) || !$(global.getPlugin(Atocha.class).reuseWar)) {
                            final TaskResult r = $.runner.run(new GrailsBuilder(global));

                            if (r.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }
                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(SessionContext localCtx) {

                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, global.var(cap.appUsername) + "." + global.var(cap.appUsername))
                );

                return strategy;
            }
        });

        return global;
    }
}
