import atocha.Atocha;
import bear.cli.Script;
import bear.core.*;
import bear.plugins.Plugin;
import bear.plugins.grails.GrailsBuilderTask;
import bear.plugins.grails.GrailsPlugin;
import bear.plugins.java.JavaPlugin;
import bear.plugins.tomcat.TomcatPlugin;
import bear.strategy.DeployStrategyTask;
import bear.vcs.VcsCLIPlugin;
import bear.strategy.SymlinkEntry;
import bear.task.TaskResult;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static bear.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearSettings extends IBearSettings {
    private static final Logger logger = LoggerFactory.getLogger(DeployStrategyTask.class);

    GrailsPlugin grails;
    JavaPlugin java;
    Bear bear;
    TomcatPlugin tomcat;

    protected BearSettings(GlobalContextFactory factory) {
        super(factory);
    }

    @Override
    public GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
        final GlobalContext global = factory.getGlobal();

        factory.globalVarsInitPhase = newAtochaSettings(global.bear);
        factory.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
            @Override
            public List<Class<? extends Plugin>> registerPlugins(VariablesLayer vars) {
                return Lists.<Class<? extends Plugin>>newArrayList(
                    TomcatPlugin.class,
                    GrailsPlugin.class,
                    JavaPlugin.class);
            }
        };

        factory.init();

        tomcat = global.getPlugin(TomcatPlugin.class);
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        bear = global.bear;

        final VariablesLayer vars = global.variablesLayer;

        vars
            .putS(grails.homePath, "/opt/grails")
            .putS(java.homePath, "/usr/java/jdk1.6.0_43")
            .putS(bear.sshUsername, "ihseus")
            .putS(bear.vcsPassword, global.getProperty("svn.password"))
        ;

        tomcat.warName.setEqualTo(grails.warName);

        bear.stages.defaultTo(
            new Stages()
                .add(new Stage("pac-dev", global)
                    .add(newUnixRemote("pac-dev", "10.22.13.4", global)))
                .add(new Stage("pac-test", global)
                    .add(newUnixRemote("pac-test", "10.22.13.6", global)))
        );

        bear.getStrategy.setDynamic(new VarFun<DeployStrategyTask>() {
            public DeployStrategyTask apply() {
                grails.projectPath.setEqualTo(
                    bear.vcsBranchLocalPath
                );

                final DeployStrategyTask strategy = new DeployStrategyTask($) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        logger.info("updating the project, please wait...");

                        StopWatch sw = new StopWatch();
                        sw.start();

                        final VcsCLIPlugin.Session vcsCLI = $(bear.vcs);

                        final String destPath = $(bear.vcsBranchLocalPath);

                        final Script line;

                        if (!$.sys.exists(destPath)) {
                            line = vcsCLI.checkout($(bear.revision), destPath, VcsCLIPlugin.emptyParams());
                        } else {
                            line = vcsCLI.sync($(bear.revision), destPath, VcsCLIPlugin.emptyParams());
                        }

                        line.timeoutMs(600 * 1000);

                        $.sys.run(line, vcsCLI.passwordCallback());

                        logger.info("done updating in {}", sw);

                        logger.info("building the project...");

                        String warPath = $(grails.releaseWarPath);

                        if (!$.sys.exists(warPath) || !$(global.getPlugin(Atocha.class).reuseWar)) {
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

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, global.var(bear.appUsername) + "." + global.var(bear.appUsername))
                );

                return strategy;
            }
        });

        System.out.printf("finished configuring Settings.java%n");

        return global;
    }

    public static GlobalContextFactory.GlobalVarsInitPhase newAtochaSettings(Bear bear1) {
        final Bear bear = bear1;

        return new GlobalContextFactory.GlobalVarsInitPhase() {
            @Override
            public void setVars(VariablesLayer vars) {
                vars
                    .putS(bear.applicationName, "atocha")
                    .putB(bear.productionDeployment, false)
                    .putB(bear.speedUpBuild, true)
                    .putS(bear.repositoryURI, "svn://vm02/svnrepos/atocha")
                    .putS(bear.appUsername, "tomcat")
                ;
            }
        };
    }
}
