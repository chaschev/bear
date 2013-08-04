package cap4j.examples;

import cap4j.*;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.scm.CommandLine;
import cap4j.scm.Vcs;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import cap4j.task.Tasks;
import com.google.common.base.Function;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static cap4j.CapConstants.*;
import static cap4j.plugins.tomcat.TomcatPlugin.tomcatWarPath;
import static cap4j.plugins.tomcat.TomcatPlugin.tomcatWebapps;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;
import static cap4j.session.VariableUtils.joinPath;

/**
 * User: achaschev
 * Date: 8/3/13
 */
public class Ex6DeployWarViaCache1 {
    private static final Logger logger = LoggerFactory.getLogger(BaseStrategy.class);


    public static void main(String[] args) throws InterruptedException {
        GlobalContextFactory.INSTANCE.globalVarsInitPhase = Ex5DeployWar1.newAtochaSettings();
        GlobalContextFactory.INSTANCE.init();

        final Variables vars = GlobalContext.INSTANCE.variables;

        vars
            .putS(GrailsPlugin.grailsPath, "/opt/grails")
            .putS(JavaPlugin.javaHomePath, "/usr/java/jdk1.6.0_43")
//            .putS(GrailsConf.projectPath, null)
        ;

        TomcatPlugin.tomcatWarName.setEqualTo(GrailsPlugin.warName);

        TomcatPlugin.init();

        final Stage pacDev = new Stage("pac-dev")
//            .add(newUnixRemote("server1", "chaschev", "1", "192.168.25.66"))
//            .add(newUnixRemote("server1", "192.168.25.66"))
            .add(newUnixRemote("pac-dev", "10.22.13.4"))
            ;

        CapConstants.newStrategy.setDynamic(new Function<VarContext, BaseStrategy>() {

            public BaseStrategy apply(VarContext ctx) {
//                GrailsConf.projectWarPath.setEqualTo(
//                    joinPath(vcsBranchLocalPath, GrailsConf.warName)
//                );

                GrailsPlugin.projectPath.setEqualTo(
                    vcsBranchLocalPath
                );

                final BaseStrategy strategy = new BaseStrategy(ctx) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        logger.info("updating the project, please wait...");

                        StopWatch sw = new StopWatch();
                        sw.start();

                        final Vcs vcs = ctx.var(CapConstants.vcs);

                        final String destPath = ctx.var(vcsBranchLocalPath);

                        final CommandLine line;

                        if(!ctx.system.exists(destPath)){
                            line = vcs.checkout(ctx.var(revision), destPath, Vcs.emptyParams());
                        }else{
                            line = vcs.sync(ctx.var(revision), destPath, Vcs.emptyParams());
                        }

                        line.timeoutMs(600 * 1000);

                        ctx.system.run(line, vcs.runCallback());

                        logger.info("done updating in {}", sw);

                        logger.info("building the project...");

                        String warPath = ctx.var(GrailsPlugin.releaseWarPath);

                        if (!ctx.system.exists(warPath) || !ctx.var(Ex5DeployWar1.AtochaConstants.reuseWar)) {
                            final GrailsBuildResult r = new GrailsBuilder(ctx).build();

                            if (r.result.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }


                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(VarContext localCtx) {

                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcatWarPath, ctx.var(appUsername) + "." + ctx.var(appUsername))
                );

                return strategy;
            }
        });

        pacDev.runTask(Tasks.deploy);

        GlobalContext.INSTANCE.shutdown();

    }
}
