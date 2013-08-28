package cap4j.plugins.grails;

import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.Plugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.scm.CommandLineResult;
import cap4j.scm.VcsCLI;
import cap4j.session.DynamicVariable;
import cap4j.session.SystemEnvironment;
import cap4j.session.VariableUtils;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import static cap4j.core.CapConstants.*;
import static cap4j.session.VariableUtils.*;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:28 PM
*/
public class GrailsPlugin extends Plugin {
    public final DynamicVariable<String>
        homePath = newVar("/var/lib/grails").setDesc("Grails root dir"),
        homeParentPath = dynamic(new Function<SessionContext, String>() {
            public String apply(SessionContext ctx) {
                return StringUtils.substringBeforeLast(ctx.var(homePath), "/");
            }
        }),
        currentVersionPath = dynamic(new Function<SessionContext, String>() {
            public String apply(SessionContext ctx) {
                return ctx.system.joinPath(ctx.var(homeParentPath), "grails-" + ctx.var(version));
            }
        }),
        grailsBin = joinPath(homePath, "bin"),
        projectPath = dynamicNotSet("Project root dir"),
        grailsExecName = dynamic("grails or grails.bat", new Function<SessionContext, String>() {
            public String apply(SessionContext ctx) {
                return "grails" + (ctx.system.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = condition(isSet(null, homePath),
            joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = newVar("ROOT.war").setDesc("i.e. ROOT.war"),
        projectWarPath = joinPath(projectPath, warName),
        releaseWarPath = condition(cap.isRemoteEnv, joinPath(cap.releasePath, warName), projectWarPath),
        version = dynamicNotSet(""),
        grailsSharedDirPath,
        grailsSharedBuildPath
    ;

    public final DynamicVariable<Boolean>
        grailsClean = VariableUtils.eql("grailsClean", cap.clean).setDesc("clean project")
    ;

    public final Task setup = new Task("setup grails") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.rm(ctx.var(grailsSharedBuildPath));
            system.mkdirs(ctx.var(grailsSharedBuildPath));

            if(!system.exists(system.joinPath(ctx.var(grailsSharedDirPath), String.format("grails-%s.zip", ctx.var(version))))){
                system.run(new VcsCLI.Script()
                    .cd(ctx.var(grailsSharedBuildPath))
                    .add(system.line().timeoutMin(60).addRaw("wget http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/grails-%s.zip", ctx.var(version))));
            }

            final String homeParentPath = StringUtils.substringBeforeLast(ctx.var(homePath), "/");

            final CommandLineResult r = system.run(new VcsCLI.Script()
                .cd(ctx.var(grailsSharedBuildPath))
                .add(system.line().timeoutMin(1).addRaw("unzip ../grails-%s.zip", ctx.var(version)))
                .add(system.line().sudo().addRaw("rm -r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("mv %s %s", ctx.var(currentVersionPath), homeParentPath))
                .add(system.line().sudo().addRaw("ln -s %s %s", ctx.var(currentVersionPath), ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod -R g+r,o+r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("rm /usr/bin/grails"))
                .add(system.line().sudo().addRaw("ln -s %s/bin/grails /usr/bin/grails", ctx.var(currentVersionPath))),
                SystemEnvironment.passwordCallback(null, ctx.var(cap.sshPassword))
            );

            System.out.println("verifying version...");
            final String installedVersion = StringUtils.substringAfter(
                system.run(system.line().setVar("JAVA_HOME", ctx.var(global.getPlugin(JavaPlugin.class).homePath)).addRaw("grails --version")).text.trim(),
                "version: ");

            Preconditions.checkArgument(ctx.var(version).equals(installedVersion),
                "versions don't match: %s (installed) vs %s (actual)", installedVersion, ctx.var(version));

            System.out.printf("successfully installed Grails %s%n", ctx.var(version));

            return new TaskResult(r);
        }
    };

    public GrailsPlugin(GlobalContext global) {
        super(global);

        grailsSharedDirPath = VariableUtils.joinPath(cap.sharedPath, "grails");
        grailsSharedBuildPath = VariableUtils.joinPath(grailsSharedDirPath, "build");
    }

    //            projectPath,
//            clean
}
