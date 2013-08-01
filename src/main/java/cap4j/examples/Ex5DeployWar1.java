package cap4j.examples;

import cap4j.*;
import cap4j.scm.BaseScm;
import cap4j.session.DynamicVariable;
import cap4j.session.Result;
import cap4j.session.VariableUtils;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import cap4j.task.Tasks;
import com.google.common.base.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static cap4j.CapConstants.*;
import static cap4j.examples.Ex5DeployWar1.TomcatConstants.tomcatWebapps;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;
import static cap4j.session.VariableUtils.joinPath;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class Ex5DeployWar1 {
    public static class AtochaConstants {
        public static final DynamicVariable<Boolean>
            reuseWar = CapConstants.bool("reuseWar", "will skip building WAR").defaultTo(false);

    }
    
    public static class TomcatConstants {
        public static final DynamicVariable<String>
            tomcatWebappsUnix = strVar("tomcatWebappsWin", "/var/lib/tomcat6/webapps").defaultTo("/var/lib/tomcat6/webapps"),
            tomcatWebappsWin = dynamicNotSet("tomcatWebappsWin", ""),
            tomcatWebapps = strVar("tomcatHome", "").setDynamic(new Function<VarContext, String>() {
                public String apply(VarContext ctx) {
                    return ctx.system.isUnix() ? ctx.varS(tomcatWebappsUnix) : ctx.varS(tomcatWebappsWin);
                }
            });

    }

    public static class GrailsBuilder {
        private static final Logger logger = LoggerFactory.getLogger(GrailsBuilder.class);

        VarContext localCtx;

        public static class GrailsConf {
            public static final DynamicVariable<String>
                grailsPath = CapConstants.dynamicNotSet("grailsPath", "Grails root dir"),
                grailsBin = VariableUtils.joinPath("grailsBin", grailsPath, "bin"),
                projectPath = CapConstants.dynamicNotSet("projectPath", "Project root dir"),
                grailsExecName = CapConstants.dynamic("grailsExec", "grails or grails.bat", new Function<VarContext, String>() {
                    public String apply(VarContext varContext) {
                        return "grails" + (varContext.system.isNativeUnix() ? "" : ".bat");
                    }
                }),
                grailsExecPath = VariableUtils.joinPath("grailsExecPath", grailsBin, grailsExecName),
                warName = strVar("warName", "i.e. ROOT.war").defaultTo("ROOT.war"),
                projectWarPath = VariableUtils.joinPath("projectWarPath", projectPath, warName)
                ;

            public static final DynamicVariable<Boolean>
                grailsClean = VariableUtils.eql("grailsClean", CapConstants.clean).setDesc("clean project")
            ;
//            projectPath,
//            clean
        }


        public GrailsBuilder(VarContext localCtx) {
            this.localCtx = localCtx;
        }

        public static class GrailsBuildResult {
            Result result;
            String path;

            public GrailsBuildResult(Result result, String path) {
                this.result = result;
                this.path = path;
            }
        }

        public GrailsBuildResult build() {
            logger.info("building Grails WAR...");

            final String s = localCtx.varS(GrailsConf.grailsPath);
            final String grailsExecPath = localCtx.varS(GrailsConf.grailsExecPath);

            String projectPath = localCtx.varS(GrailsConf.projectPath);

            final BaseScm.Script script = new BaseScm.Script()
                .cd(projectPath);

            if (localCtx.varB(GrailsConf.grailsClean)) {
                script
                    .add(new BaseScm.CommandLine().a(
                        grailsExecPath,
                        "clean"
                    ));
            }

            final String warName = localCtx.gvar(GrailsConf.warName);

            script.add(
                new BaseScm.CommandLine().a(
                    grailsExecPath,
                    "war",
                    warName));

            final BaseScm.CommandLineResult clResult = localCtx.system.run(script);

            return new GrailsBuildResult(clResult.result, localCtx.joinPath(projectPath, warName));
        }
    }

    public static void main(String[] args) throws InterruptedException {

        GlobalContextFactory.INSTANCE.globalVarsInitPhase = new GlobalContextFactory.GlobalVarsInitPhase() {
            @Override
            public void setVars(Variables vars) {
                vars
                    .putS(applicationName, "atocha")
                    .putS(GrailsBuilder.GrailsConf.grailsPath, "c:/dev/grails-2.1.0")
                    .putS(GrailsBuilder.GrailsConf.projectPath, "c:/Users/achaschev/prj/atocha")
                    .putB(productionDeployment, false)
                    .putB(speedUpBuild, true)
                    .putB(AtochaConstants.reuseWar, true)
                    .putS(scm, "svn")
                    .putS(repositoryURI, "svn+ssh://dev.afoundria.com/var/svn/repos/atocha/trunk")

                    .putS(sshUsername, "chaschev")
                    .putS(sshPassword, "aaaaaa")
                    .putS(appUsername, "tomcat")
                ;
            }
        };

        GlobalContextFactory.INSTANCE.init();

        final Stage pacDev = new Stage("pac-dev")
//            .add(newUnixRemote("server1", "chaschev", "1", "192.168.25.66"))
            .add(newUnixRemote("server1", "192.168.25.66"))

            ;

        CapConstants.newStrategy.setDynamic(new Function<VarContext, BaseStrategy>() {
            public BaseStrategy apply(VarContext ctx) {
                final BaseStrategy strategy = new BaseStrategy(ctx) {
                    @Override
                    protected void step_10_getPrepareRemoteData() {

                    }

                    @Override
                    protected List<File> step_20_prepareLocalFiles(VarContext localCtx) {
                        File rootWar = new File(localCtx.var(GrailsBuilder.GrailsConf.projectWarPath));

                        if (!rootWar.exists() || !localCtx.var(AtochaConstants.reuseWar)) {
                            final GrailsBuilder.GrailsBuildResult r = new GrailsBuilder(localCtx).build();

                            if (r.result.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }

                        return Collections.singletonList(rootWar);
                    }

                    @Override
                    protected void step_30_copyFilesToHosts() {

                    }

                    @Override
                    protected void step_40_updateRemoteFiles() {
                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(VarContext localCtx) {

                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", joinPath("tomcatWebapps/ROOT.war", tomcatWebapps, "ROOT.war"),
                        ctx.var(appUsername) + "." + ctx.var(appUsername)
                        )
                );

                return strategy;
            }
        });

//        Tasks.setup.addBeforeTask(new Task() {
//            @Override
//            protected TaskResult run(TaskRunner runner) {
//                return OK;
//            }
//        });

        Tasks.restartApp.addBeforeTask(new Task("restart tomcat") {
            @Override
            protected TaskResult run(TaskRunner runner) {
                //todo restart tomcat
                system.sudo().run(new BaseScm.CommandLine().a("service", "tomcat6", "restart"));

                return new TaskResult(Result.OK);
            }
        });

        pacDev.runTask(Tasks.deploy);

        GlobalContext.INSTANCE.taskExecutor.shutdown();
        GlobalContext.INSTANCE.taskExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
