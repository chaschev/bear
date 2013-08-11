import cap4j.core.*;
import cap4j.examples.Ex5DeployWar1;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.scm.CommandLine;
import cap4j.scm.VcsCLI;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import com.google.common.base.Function;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static cap4j.core.CapConstants.*;
import static cap4j.core.GlobalContext.INSTANCE;
import static cap4j.core.GlobalContext.var;
import static cap4j.plugins.tomcat.TomcatPlugin.tomcatWarPath;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * User: achaschev
 * Date: 8/5/13
 */
public class Settings implements Callable<Void> {
    private static final Logger logger = LoggerFactory.getLogger(BaseStrategy.class);


    @Override
    public Void call() throws Exception{
        GlobalContextFactory.INSTANCE.globalVarsInitPhase = Ex5DeployWar1.newAtochaSettings();
        GlobalContextFactory.INSTANCE.init();

        final Variables vars = INSTANCE.variables;

        vars
            .putS(GrailsPlugin.grailsPath, "/opt/grails")
            .putS(JavaPlugin.javaHomePath, "/usr/java/jdk1.6.0_43")
            .putS(sshUsername, "ihseus")
            .putS(vcsPassword, "ihs3Us3r1")
            .putS(sshPassword, "ihs3Us3r2")
        ;

        TomcatPlugin.tomcatWarName.setEqualTo(GrailsPlugin.warName);

        TomcatPlugin.init();

        stages.defaultTo(
            new Stages()
                .add(new Stage("pac-dev")
                    .add(newUnixRemote("pac-dev", "10.22.13.4")))
        );

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

                        final VcsCLI vcsCLI = ctx.var(CapConstants.vcs);

                        final String destPath = ctx.var(vcsBranchLocalPath);

                        final CommandLine line;

                        if(!ctx.system.exists(destPath)){
                            line = vcsCLI.checkout(ctx.var(revision), destPath, VcsCLI.emptyParams());
                        }else{
                            line = vcsCLI.sync(ctx.var(revision), destPath, VcsCLI.emptyParams());
                        }

                        line.timeoutMs(600 * 1000);

                        ctx.system.run(line, vcsCLI.runCallback());

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

        return null;
    }
}
