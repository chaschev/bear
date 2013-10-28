import atocha.Atocha;
import bear.cli.Script;
import bear.core.*;
import bear.plugins.grails.GrailsBuilderTask;
import bear.plugins.grails.GrailsPlugin;
import bear.plugins.java.JavaPlugin;
import bear.plugins.tomcat.TomcatPlugin;
import bear.strategy.DeployStrategyTaskDef;
import bear.strategy.SymlinkEntry;
import bear.task.TaskResult;
import bear.vcs.VcsCLIPlugin;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearSettings extends IBearSettings {
    private static final Logger logger = LoggerFactory.getLogger(DeployStrategyTaskDef.class);

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

        factory.init(this);

        final VariablesLayer vars = global.getLayer();

        vars
            .put(grails.homePath, "/opt/grails")
            .put(bear.sshUsername, "ihseus")
            .put(bear.vcsPassword, global.getProperty("svn.password"))
        ;

        tomcat.warName.setEqualTo(grails.warName);

        bear.stages.defaultTo(
            new Stages()
                .add(new Stage("pac-dev", global)
                    .add("pac-dev", "10.22.13.4"))
                .add(new Stage("pac-test", global)
                    .add("pac-test", "10.22.13.6"))
        );

        bear.getStrategy.setDynamic(new Fun<DeployStrategyTaskDef, SessionContext>() {
            public DeployStrategyTaskDef apply(final SessionContext $) {
                grails.projectPath.setEqualTo(
                    bear.vcsBranchLocalPath
                );

                final DeployStrategyTaskDef strategy = new DeployStrategyTaskDef($) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        logger.info("updating the project, please wait...");

                        StopWatch sw = new StopWatch();
                        sw.start();

                        final VcsCLIPlugin.Session vcsCLI = $.var(bear.vcs);

                        final String destPath = $.var(bear.vcsBranchLocalPath);

                        final Script line;

                        if (!$.sys.exists(destPath)) {
                            line = vcsCLI.checkout($.var(bear.revision), destPath, VcsCLIPlugin.emptyParams());
                        } else {
                            line = vcsCLI.sync($.var(bear.revision), destPath, VcsCLIPlugin.emptyParams());
                        }

                        line.timeoutMs(600 * 1000);

                        $.sys.run(line, vcsCLI.passwordCallback());

                        logger.info("done updating in {}", sw);

                        logger.info("building the project...");

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
                    .put(bear.applicationName, "atocha")
                    .putB(bear.productionDeployment, false)
                    .putB(bear.speedUpBuild, true)
                    .put(bear.repositoryURI, "svn://vm02/svnrepos/atocha")
                    .put(bear.appUsername, "tomcat")
                ;
            }
        };
    }
}
