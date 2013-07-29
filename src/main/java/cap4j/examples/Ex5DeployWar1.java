package cap4j.examples;

import cap4j.CapConstants;
import cap4j.Nameable;
import cap4j.Stage;
import cap4j.Variables;
import cap4j.scm.BaseScm;
import cap4j.session.DynamicVariable;
import cap4j.session.GenericUnixRemoteEnvironment;
import cap4j.session.Result;
import cap4j.strategy.BaseStrategy;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import cap4j.task.Tasks;
import com.google.common.base.Function;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static cap4j.CapConstants.*;
import static cap4j.examples.Ex5DeployWar1.GrailsBuilder.warName;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class Ex5DeployWar1 {
    public static class TomcatConstants{
        public static final DynamicVariable<String>
            tomcatWebappsUnix = strVar("tomcatWebappsWin", "/var/lib/tomcat6/webapps").defaultTo("/var/lib/tomcat6/webapps"),
            tomcatWebappsWin = dynamicNotSet("tomcatWebappsWin", ""),
            tomcatWebapps = strVar("tomcatHome", "").setDynamic(new Function<Variables.Context, String>() {
                public String apply( Variables.Context ctx) {
                    return ctx.system.isUnix() ? ctx.varS(tomcatWebappsUnix) : ctx.varS(tomcatWebappsWin);
                }
            });

    }

    public static class GrailsBuilder{
        Variables.Context localCtx;

        public static enum GrailsConf implements Nameable{
            grailsPath,
            projectPath,
            clean
        }

        public static final DynamicVariable<String> warName = strVar("warName", "i.e. ROOT.war").defaultTo("ROOT.war");

        public GrailsBuilder(Variables.Context localCtx) {
            this.localCtx = localCtx;
        }

        public static class GrailsBuildResult{
            Result result;
            String path;

            public GrailsBuildResult(Result result, String path) {
                this.result = result;
                this.path = path;
            }
        }

        public GrailsBuildResult build(){
            final String grailsExecPath = localCtx.joinPath(
                localCtx.varS(GrailsConf.grailsPath),
                "bin", "grails"
            );

            String projectPath = localCtx.varS(GrailsConf.projectPath);

            final BaseScm.Script script = new BaseScm.Script()
                .cd(projectPath);

            if(localCtx.varB(GrailsConf.clean)){
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

            return new GrailsBuildResult(localCtx.system.run(script).result, localCtx.joinPath(projectPath, warName));
        }
    }

    public static void main(String[] args) {
        new GenericUnixRemoteEnvironment()
            .setSshAddress(
                new GenericUnixRemoteEnvironment.SshAddress("chaschev", "aaaaaa", "192.168.25.66")
            );

        final Stage pacDev = new Stage("pac-dev")
            .add(newUnixRemote("chaschev", "1", "192.168.25.66"))
            ;

        CapConstants.newStrategy.setDynamic(new Function<Variables.Context, BaseStrategy>() {
            public BaseStrategy apply(@Nullable Variables.Context input) {
                return new BaseStrategy() {
                    @Override
                    protected void step_10_getPrepareRemoteData() {

                    }

                    @Override
                    protected List<File> step_20_prepareLocalFiles(Variables.Context localCtx) {
                        final GrailsBuilder.GrailsBuildResult r = new GrailsBuilder(localCtx).build();



                        if(r.result.nok()){
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
                            ctx.joinPath(ctx.gvar(currentPath), ctx.gvar(warName)),
                            ctx.joinPath(ctx.varS(TomcatConstants.tomcatWebapps), ctx.gvar(warName)));
                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(Variables.Context localCtx) {

                    }


                };
            }
        });

        Tasks.RESTART_APP.addBeforeTask(new Task(){
            @Override
            protected TaskResult run(TaskRunner runner) {
                //todo restart tomcat
            }
        });

        pacDev.runTask(Tasks.DEPLOY);
    }
}
