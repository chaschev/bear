package cap4j.plugins;

import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.java.JavaPlugin;
import cap4j.scm.VcsCLI;
import cap4j.session.DynamicVariable;
import cap4j.task.Task;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import static cap4j.core.CapConstants.*;
import static cap4j.session.VariableUtils.concat;

/**
 * User: chaschev
 * Date: 8/30/13
 */


/**
 * A class that simplifies operations (i.e. installation) of tools like Maven, Grails, Play, Tomcat, etc
 */
public class ZippedToolPlugin extends Plugin{
    public final DynamicVariable<String>
        version = dynamicNotSet("version of the tool"),
        toolname = dynamicNotSet("i.e. maven"),
        toolDistrName = strVar("i.e. apache-tomcat").setEqualTo(toolname),
        versionName = concat(toolDistrName, "-", version).setDesc("i.e. apache-maven-3.0.5"),
        distrFilename = concat(versionName, ".tar.gz"),
        homePath = concat("/var/lib/", toolname).setDesc("Tool root dir"),
        homeParentPath = dynamic(new VarFun<String>() {
            public String apply() {
                return StringUtils.substringBeforeLast(ctx.var(homePath), "/");
            }
        }),
        homeVersionPath = concat(homeParentPath, "/", versionName).setDesc("i.e. /var/lib/apache-maven-7.0.42"),
        currentVersionPath = concat(homeParentPath, "/", versionName),

        myDirPath,
        buildPath,

        distrWwwAddress = dynamicNotSet("distribution download address");

    public ZippedToolPlugin(GlobalContext global) {
        super(global);
        myDirPath = concat(cap.sharedPath, "/", toolname).setDesc("a path in a shared dir, i.e. /var/lib/<app-name>/shared/maven");
        buildPath = concat(myDirPath, "/build");
    }

    protected abstract class ZippedToolTask extends Task {
        protected ZippedToolTask(String name) {
            super(name);
        }

        protected void clean(){
            system.rm(ctx.var(buildPath));
            system.mkdirs(ctx.var(buildPath));
        }

        protected void download(){
            if(!system.exists(system.joinPath(ctx.var(myDirPath), ctx.var(distrFilename)))){
                system.run(new VcsCLI.Script()
                    .cd(ctx.var(myDirPath))
                    .add(system.line().timeoutMin(60).addRaw("wget %s", ctx.var(distrWwwAddress))));
            }
        }

        protected VcsCLI.Script extractToHomeScript;

        protected abstract String extractVersion(String output);
        protected abstract String createVersionCommandLine();

        protected VcsCLI.Script extractToHomeDir(){
            final String _distrFilename = ctx.var(distrFilename);

            extractToHomeScript = new VcsCLI.Script()
                .cd(ctx.var(buildPath));

            if(_distrFilename.endsWith("tar.gz")){
                extractToHomeScript.add(system.line().timeoutMin(1).addRaw("tar xvfz ../%s", _distrFilename));
            }else
            if(_distrFilename.endsWith("zip")){
                extractToHomeScript.add(system.line().timeoutMin(1).addRaw("unzip ../%s", ctx.var(distrFilename)));
            }

            extractToHomeScript
                .add(system.line().sudo().addRaw("rm -r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("rm -r %s", ctx.var(homeVersionPath)))
                .add(system.line().sudo().addRaw("mv %s %s", ctx.var(buildPath) + "/" + ctx.var(versionName), ctx.var(homeParentPath)))
                .add(system.line().sudo().addRaw("ln -s %s %s", ctx.var(currentVersionPath), ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod -R g+r,o+r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", ctx.var(homePath)));

            return extractToHomeScript;
        }

        protected VcsCLI.Script shortCut(String newCommandName, String sourceExecutableName){
            return extractToHomeScript.add(system.line().sudo().addRaw("rm /usr/bin/%s", newCommandName))
                .add(system.line().sudo().addRaw("ln -s %s/bin/%s /usr/bin/mvn", ctx.var(homePath), sourceExecutableName, newCommandName));
        }

        protected void verifyVersion(){
            System.out.println("verifying version...");
            final String versionText = system.run(system.line().setVar("JAVA_HOME", ctx.var(global.getPlugin(JavaPlugin.class).homePath)).addRaw(createVersionCommandLine())).text.trim();
            final String installedVersion = extractVersion(versionText);

            Preconditions.checkArgument(ctx.var(version).equals(installedVersion),
                "versions don't match: %s (installed) vs %s (actual)", installedVersion, ctx.var(version));

            System.out.printf("successfully installed %s%n", ctx.var(versionName));
        }
    }
}
