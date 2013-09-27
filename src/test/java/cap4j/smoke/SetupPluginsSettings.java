package cap4j.smoke;

import atocha.Atocha;
import cap4j.core.*;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.MavenPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.scm.GitCLIPlugin;
import cap4j.session.Result;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
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
                );
            }
        };

        factory.init();

        tomcat = global.getPlugin(TomcatPlugin.class);
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        cap = global.cap;

        tomcat.warName.setEqualTo(grails.warName);

        grails.version.defaultTo("2.3.0", true);

        cap.stages.defaultTo(
            new Stages()
                .add(new Stage("vm02", global)
                    .add(newUnixRemote("vm02", "vm02", global)))
        );

        Cap.newStrategy.setDynamic(new VarFun<BaseStrategy>() {

            public BaseStrategy apply() {

                grails.projectPath.setEqualTo(
                    cap.vcsBranchLocalPath
                );

                final BaseStrategy strategy = new BaseStrategy<BaseStrategy>($, global) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        $.runner.run(global.tasks.vcsUpdate);

                        $.log("building the project...");

                        String warPath = $(grails.releaseWarPath);

                        if (!$.sys.exists(warPath) || !$(global.getPlugin(Atocha.class).reuseWar)) {
                            final Result r = $.runner.run(new GrailsBuilder(global));

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
