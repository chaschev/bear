package cap4j.examples;

import atocha.Atocha;
import cap4j.core.*;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.mysql.MySqlPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.scm.CommandLine;
import cap4j.scm.VcsCLI;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static cap4j.core.GlobalContext.*;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * User: achaschev
 * Date: 8/3/13
 */
public class Ex6DeployWarViaCache1 {
    private static final Logger logger = LoggerFactory.getLogger(BaseStrategy.class);

    public static void main(String[] args) throws InterruptedException {
        GlobalContextFactory.INSTANCE.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
            @Override
            public List<Class<? extends Plugin>> registerPlugins(Variables vars) {
                return Lists.<Class<? extends Plugin>>newArrayList(
                    TomcatPlugin.class,
                    GrailsPlugin.class,
                    MySqlPlugin.class,
                    JavaPlugin.class
                );
            }
        };
        //todo this is not good
        GlobalContextFactory.INSTANCE.globalVarsInitPhase = Ex5DeployWar1.newAtochaSettings(GlobalContextFactory.INSTANCE.getGlobal().cap);
        GlobalContextFactory.INSTANCE.init();

        final GlobalContext global = getInstance();
        final Variables vars = global.variables;

        final GrailsPlugin grails = plugin(GrailsPlugin.class);
        final JavaPlugin java = plugin(JavaPlugin.class);
        final TomcatPlugin tomcat = plugin(TomcatPlugin.class);
        final MySqlPlugin mysql = plugin(MySqlPlugin.class);

        final CapConstants cap = global.cap;

        vars
//            .putS(grails.homePath, "/opt/grails")
//            .putS(java.homePath, "/usr/java/jdk1.6.0_43")
            .putS(cap.sshUsername, "andrey")
            .putS(cap.vcsPassword, global.getProperty("svn.password"))
            .putS(cap.stage, "vms")
            .putS(mysql.dbName, "demodb")
            .putS(java.javaLinuxDistributionName, "jdk-7u25-linux-x64.gz")
            .putS(grails.version, "2.2.4")
//            .putS(vcsBranchName, "branches/rc3_r1201")
        ;

//        vars.putB(clean, true);

        tomcat.warName.setEqualTo(grails.warName);

        cap.stages.defaultTo(
            new Stages()
                .add(new Stage("vms", global)
                    .add(newUnixRemote("vm02", "vm02", global)))
        );

        CapConstants.newStrategy.setDynamic(new Function<SessionContext, BaseStrategy>() {

            public BaseStrategy apply(SessionContext ctx) {
//                GrailsConf.projectWarPath.setEqualTo(
//                    joinPath(vcsBranchLocalPath, GrailsConf.warName)
//                );

                grails.projectPath.setEqualTo(
                    cap.vcsBranchLocalPath
                );

                final BaseStrategy strategy = new BaseStrategy(ctx, global) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        logger.info("updating the project, please wait...");

                        StopWatch sw = new StopWatch();
                        sw.start();

                        final VcsCLI vcsCLI = ctx.var(cap.vcs);

                        final String destPath = ctx.var(cap.vcsBranchLocalPath);

                        final CommandLine line;

                        if(!ctx.system.exists(destPath)){
                            line = vcsCLI.checkout(ctx.var(cap.revision), destPath, VcsCLI.emptyParams());
                        }else{
                            line = vcsCLI.sync(ctx.var(cap.revision), destPath, VcsCLI.emptyParams());
                        }

                        line.timeoutMs(600 * 1000);

                        ctx.system.run(line, vcsCLI.passwordCallback());

                        logger.info("done updating in {}", sw);

                        logger.info("building the project...");

                        String warPath = ctx.var(grails.releaseWarPath);

                        final boolean warExists = ctx.system.exists(warPath);
                        if (!warExists || !ctx.var(global.getPlugin(Atocha.class).reuseWar)) {
                            final GrailsBuildResult r = new GrailsBuilder(ctx, global).build();

                            if (r.result.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }else{
                            logger.info("war exists and will be reused");
                        }
                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(SessionContext localCtx) {

                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, ctx.var(cap.appUsername) + "." + ctx.var(cap.appUsername))
                );

                return strategy;
            }
        });

        global.localCtx.var(cap.getStage).runTask(grails.setup);

        global.shutdown();
    }
}
