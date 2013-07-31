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

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static cap4j.CapConstants.*;
import static cap4j.examples.Ex5DeployWar1.GrailsBuilder.warName;
import static cap4j.examples.Ex5DeployWar1.TomcatConstants.tomcatWebapps;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;
import static cap4j.session.VariableUtils.joinPath;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class Ex5DeployWar1 {
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
                grailsExecPath = VariableUtils.joinPath("grailsExecPath", grailsBin, grailsExecName)
                ;

            public static final DynamicVariable<Boolean>
                grailsClean = VariableUtils.eql("grailsClean", CapConstants.clean).setDesc("clean project")
            ;
//            projectPath,
//            clean
        }

        public static final DynamicVariable<String> warName = strVar("warName", "i.e. ROOT.war").defaultTo("ROOT.war");

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

            final String warName = localCtx.gvar(GrailsBuilder.warName);

            script.add(
                new BaseScm.CommandLine().a(
                    grailsExecPath,
                    "war",
                    warName));

            final BaseScm.CommandLineResult clResult = localCtx.system.run(script);

            return new GrailsBuildResult(clResult.result, localCtx.joinPath(projectPath, warName));
        }
    }

    public static void main(String[] args) {

        GlobalContextFactory.INSTANCE.globalVarsInitPhase = new GlobalContextFactory.GlobalVarsInitPhase() {
            @Override
            public void setVars(Variables vars) {
                vars
                    .putS(GrailsBuilder.GrailsConf.grailsPath, "c:/dev/grails-2.1.0")
                    .putS(GrailsBuilder.GrailsConf.projectPath, "c:/Users/achaschev/prj/atocha")
                    .putB(productionDeployment, false)
                    .putB(speedUpBuild, true)
                ;
            }
        };

        GlobalContextFactory.INSTANCE.init();

        final Stage pacDev = new Stage("pac-dev")
            .add(newUnixRemote("server1", "chaschev", "1", "192.168.25.66"));

        CapConstants.newStrategy.setDynamic(new Function<VarContext, BaseStrategy>() {
            public BaseStrategy apply(@Nullable VarContext input) {
                final BaseStrategy strategy = new BaseStrategy() {
                    @Override
                    protected void step_10_getPrepareRemoteData() {

                    }

                    @Override
                    protected List<File> step_20_prepareLocalFiles(VarContext localCtx) {
                        final GrailsBuilder.GrailsBuildResult r = new GrailsBuilder(localCtx).build();

                        if (r.result.nok()) {
                            throw new IllegalStateException("failed to build WAR");
                        }

                        return Collections.singletonList(new File(r.path));
                    }

                    @Override
                    protected void step_30_copyFilesToHosts() {

                    }

                    @Override
                    protected void step_40_updateRemoteFiles() {
                        //todo stop tomcat, delete files
                        ctx.system.link(
                            ctx.varS(VariableUtils.joinPath("current/war", currentPath, warName)),
                            ctx.varS(VariableUtils.joinPath("tomcatWebapps/war", tomcatWebapps, warName)));
                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(VarContext localCtx) {

                    }
                };

                strategy.getSymlinkRules().add(new SymlinkEntry("/ROOT.war", joinPath("tomcatWebapps/ROOT.war", tomcatWebapps, "ROOT.war")));

                return strategy;
            }
        });

        Tasks.restartApp.addBeforeTask(new Task() {
            @Override
            protected TaskResult run(TaskRunner runner) {
                //todo restart tomcat
                system.run(new BaseScm.CommandLine().a("service", "tomcat6", "restart"));

                return new TaskResult(Result.OK);
            }
        });

        pacDev.runTask(Tasks.deploy);
    }
}
