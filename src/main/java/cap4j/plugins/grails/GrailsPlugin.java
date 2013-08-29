package cap4j.plugins.grails;

import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
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
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

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
        homeParentPath = dynamic(new VarFun<String>() {
            public String apply() {
                return StringUtils.substringBeforeLast(ctx.var(homePath), "/");
            }
        }),
        currentVersionPath = dynamic(new VarFun<String>() {
            public String apply() {
                return ctx.system.joinPath(ctx.var(homeParentPath), "grails-" + ctx.var(version));
            }
        }),
        grailsBin = joinPath(homePath, "bin"),
        projectPath = dynamicNotSet("Project root dir"),
        grailsExecName = dynamic("grails or grails.bat", new VarFun<String>() {
            public String apply() {
                return "grails" + (ctx.system.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = condition(isSet(null, homePath),
            joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = newVar("ROOT.war").setDesc("i.e. ROOT.war"),
        projectWarPath = joinPath(projectPath, warName),
        releaseWarPath = condition(cap.isRemoteEnv, joinPath(cap.releasePath, warName), projectWarPath),
        version = dynamicNotSet(""),
        myDirPath,
        buildPath,
        distrFilename = dynamic(new VarFun<String>() {
            public String apply() {
                return "grails-" + ctx.var(version) + ".zip";
            }
        }),
        distrWwwAddress = dynamic(new VarFun<String>() {
            public String apply() {
                return MessageFormat.format("http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/%s", ctx.var(distrFilename));
            }
        })
    ;

    public final DynamicVariable<Boolean>
        clean = VariableUtils.eql(cap.clean).setDesc("clean project before build")
    ;

    public final Task setup = new Task("setup grails") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.rm(ctx.var(buildPath));
            system.mkdirs(ctx.var(buildPath));

            if(!system.exists(system.joinPath(ctx.var(myDirPath), ctx.var(distrFilename)))){
                system.run(new VcsCLI.Script()
                    .cd(ctx.var(buildPath))
                    .add(system.line().timeoutMin(60).addRaw(ctx.var(distrWwwAddress))));
            }

            final String homeParentPath = StringUtils.substringBeforeLast(ctx.var(homePath), "/");

            final CommandLineResult r = system.run(new VcsCLI.Script()
                .cd(ctx.var(buildPath))
                .add(system.line().timeoutMin(1).addRaw("unzip ../%s", ctx.var(distrFilename)))
                .add(system.line().sudo().addRaw("rm -r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("mv %s %s", ctx.var(currentVersionPath), homeParentPath))
                .add(system.line().sudo().addRaw("ln -s %s %s", ctx.var(currentVersionPath), ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod -R g+r,o+r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("rm /usr/bin/grails"))
                .add(system.line().sudo().addRaw("ln -s %s/bin/grails /usr/bin/grails", ctx.var(currentVersionPath))),
                SystemEnvironment.passwordCallback(ctx.var(cap.sshPassword))
            );

            System.out.println("verifying version...");
            final String installedVersion = StringUtils.substringAfter(
                system.run(system.line().timeoutSec(20).setVar("JAVA_HOME", ctx.var(global.getPlugin(JavaPlugin.class).homePath)).addRaw("grails --version")).text.trim(),
                "version: ");

            Preconditions.checkArgument(ctx.var(version).equals(installedVersion),
                "versions don't match: %s (installed) vs %s (actual)", installedVersion, ctx.var(version));

            System.out.printf("successfully installed Grails %s%n", ctx.var(version));

            return new TaskResult(r);
        }
    };

    public GrailsPlugin(GlobalContext global) {
        super(global);

        myDirPath = VariableUtils.joinPath(cap.sharedPath, "grails");
        buildPath = VariableUtils.joinPath(myDirPath, "build");
    }

    //            projectPath,
//            clean
}
