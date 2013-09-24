import atocha.Atocha;
import cap4j.core.*;
import cap4j.examples.Ex5DeployWar1;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.cli.CommandLine;
import cap4j.scm.VcsCLI;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * User: achaschev
 * Date: 8/5/13
 */
public class CapSettings implements ICapSettings {
    private static final Logger logger = LoggerFactory.getLogger(BaseStrategy.class);

    GrailsPlugin grails;
    JavaPlugin java;
    CapConstants cap;
    TomcatPlugin tomcat;

    @Override
    public GlobalContext configure(GlobalContextFactory factory) throws Exception{
        final GlobalContext global = factory.getGlobal();

        factory.globalVarsInitPhase = Ex5DeployWar1.newAtochaSettings(global.cap);
        factory.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
            @Override
            public List<Class<? extends Plugin>> registerPlugins(Variables vars) {
                return Lists.newArrayList(
                    TomcatPlugin.class,
                    GrailsPlugin.class,
                    JavaPlugin.class);
            }
        };

        factory.init();

        tomcat = global.getPlugin(TomcatPlugin.class);
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        cap = global.cap;

        final Variables vars = global.variables;

        vars
            .putS(grails.homePath, "/opt/grails")
            .putS(java.homePath, "/usr/java/jdk1.6.0_43")
            .putS(cap.sshUsername, "ihseus")
            .putS(cap.vcsPassword, global.getProperty("svn.password"))
        ;

        tomcat.warName.setEqualTo(grails.warName);

        cap.stages.defaultTo(
            new Stages()
                .add(new Stage("pac-dev", global)
                    .add(newUnixRemote("pac-dev", "10.22.13.4", global)))
                .add(new Stage("pac-test", global)
                    .add(newUnixRemote("pac-test", "10.22.13.6", global)))
        );

        CapConstants.newStrategy.setDynamic(new VarFun<BaseStrategy>() {

            public BaseStrategy apply() {
//                GrailsConf.projectWarPath.setEqualTo(
//                    joinPath(vcsBranchLocalPath, GrailsConf.warName)
//                );

                grails.projectPath.setEqualTo(
                    cap.vcsBranchLocalPath
                );

                final BaseStrategy strategy = new BaseStrategy($, global) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        logger.info("updating the project, please wait...");

                        StopWatch sw = new StopWatch();
                        sw.start();

                        final VcsCLI vcsCLI = $.var(cap.vcs);

                        final String destPath = $.var(cap.vcsBranchLocalPath);

                        final CommandLine line;

                        if(!$.system.exists(destPath)){
                            line = vcsCLI.checkout($.var(cap.revision), destPath, VcsCLI.emptyParams());
                        }else{
                            line = vcsCLI.sync($.var(cap.revision), destPath, VcsCLI.emptyParams());
                        }

                        line.timeoutMs(600 * 1000);

                        $.system.run(line, vcsCLI.passwordCallback());

                        logger.info("done updating in {}", sw);

                        logger.info("building the project...");

                        String warPath = $.var(grails.releaseWarPath);

                        if (!$.system.exists(warPath) || !$.var(global.getPlugin(Atocha.class).reuseWar)) {
                            final GrailsBuildResult r = new GrailsBuilder($, global).build();

                            if (r.result.nok()) {
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

        System.out.printf("finished configuring Settings.java%n");

        return global;
    }
}
