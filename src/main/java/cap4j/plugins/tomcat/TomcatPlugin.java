package cap4j.plugins.tomcat;

import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.Plugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.scm.CommandLineResult;
import cap4j.scm.VcsCLI;
import cap4j.session.DynamicVariable;
import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import cap4j.session.VariableUtils;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import com.google.common.base.Preconditions;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

import static cap4j.core.CapConstants.*;
import static cap4j.session.VariableUtils.concat;
import static cap4j.session.VariableUtils.condition;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:24 PM
*/
public class TomcatPlugin extends Plugin {
    public final DynamicVariable<String>
        version = newVar("7.0.42"),
        versionName = concat("apache-tomcat-", version),
        distrFilename = concat("apache-tomcat-", version, ".tar.gz"),
        homePath = newVar("/var/lib/tomcat").setDesc("Tomcat root dir"),
        homeParentPath = dynamic(new VarFun<String>() {
            public String apply() {
                return StringUtils.substringBeforeLast(ctx.var(homePath), "/");
            }
        }),
        homeVersionPath = concat(homeParentPath, "/", versionName).setDesc("i.e. /var/lib/tomcat-7.0.42"),
        currentVersionPath = concat(homeParentPath, "/", versionName),

        webappsUnix = strVar("webappsWin", "/var/lib/tomcat6/webapps").defaultTo("/var/lib/tomcat6/webapps"),
        webappsWin = dynamicNotSet("webappsWin", ""),
        webapps,
        warName = strVar("warName", "i.e. ROOT.war"),
        warPath,

        tomcatBasePort = newVar("8005"),
        tomcatAjpPort = newVar("8009"),
        tomcatHttpPort = newVar("8080"),
        tomcatHttpsPort = newVar("8443"),
        keystrokePassword = dynamicNotSet(""),
        catalinaHome = newVar("/usr/share/tomcat6"),
        catalinaExecutable = newVar("/usr/sbin/tomcat6"),

        myDirPath,
        buildPath,

        distrWwwAddress = dynamic(new VarFun<String>() {
            public String apply() {
                return MessageFormat.format("http://apache-mirror.rbc.ru/pub/apache/tomcat/tomcat-7/v{0}/bin/apache-tomcat-{0}.tar.gz", ctx.var(version));
            }
        })
    ;

    public TomcatPlugin(GlobalContext global) {
        super(global);

        myDirPath = VariableUtils.joinPath(cap.sharedPath, "tomcat");
        buildPath = VariableUtils.joinPath(myDirPath, "build");
        webapps = condition(cap.isUnix, webappsUnix, webappsWin);
        warPath = VariableUtils.joinPath("warPath", webapps, warName);
    }

    public void init(){
        global.tasks.restartApp.addBeforeTask(new Task("restart tomcat") {
            @Override
            protected TaskResult run(TaskRunner runner) {
                system.sudo().rm(ctx.var(warCacheDirs));
                system.sudo().run(ctx.newCommandLine()
                    .a("service", "tomcat6", "stop")
                    .semicolon()
                    .sudo()
                    .a("service", "tomcat6", "start")
                    .timeoutMin(2)
                );

                return new TaskResult(Result.OK);
            }
        });
    }

    public final Task setup = new Task("setup tomcat") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.rm(ctx.var(buildPath));
            system.mkdirs(ctx.var(buildPath));

            if(!system.exists(system.joinPath(ctx.var(myDirPath), ctx.var(distrFilename)))){
                system.run(new VcsCLI.Script()
                    .cd(ctx.var(myDirPath))
                    .add(system.line().timeoutMin(60).addRaw("wget %s", ctx.var(distrWwwAddress))));
            }

            final String homeParentPath = StringUtils.substringBeforeLast(ctx.var(homePath), "/");

            final CommandLineResult r = system.run(new VcsCLI.Script()
                .cd(ctx.var(buildPath))
                .add(system.line().timeoutMin(1).addRaw("tar xvfz ../%s", ctx.var(distrFilename)))
                .add(system.line().sudo().addRaw("rm -r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("rm -r %s", ctx.var(homeVersionPath)))
                .add(system.line().sudo().addRaw("mv %s %s", ctx.var(buildPath) + "/" + ctx.var(versionName), homeParentPath))
                .add(system.line().sudo().addRaw("ln -s %s %s", ctx.var(currentVersionPath), ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod -R g+r,o+r %s", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", ctx.var(homePath)))

                .add(system.line().sudo().addRaw("rm /usr/bin/tomcatStart"))
                .add(system.line().sudo().addRaw("ln -s %s/bin/startup.sh /usr/bin/tomcatStart", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("rm /usr/bin/tomcatStop"))
                .add(system.line().sudo().addRaw("ln -s %s/bin/shutdown.sh /usr/bin/tomcatStop", ctx.var(homePath)))
                .add(system.line().sudo().addRaw("rm /usr/bin/tomcatVersion"))
                .add(system.line().sudo().addRaw("ln -s %s/bin/version.sh /usr/bin/tomcatVersion", ctx.var(homePath))),

                SystemEnvironment.passwordCallback(ctx.var(cap.sshPassword))
            );

            System.out.println("verifying version...");
            final String versionText = system.run(system.line().setVar("JAVA_HOME", ctx.var(global.getPlugin(JavaPlugin.class).homePath)).addRaw("tomcatVersion")).text.trim();
            final String installedVersion = StringUtils.substringBetween(
                versionText,
                "Server version: Apache Tomcat/", "\r");

            Preconditions.checkArgument(ctx.var(version).equals(installedVersion),
                "versions don't match: %s (installed) vs %s (actual)", installedVersion, ctx.var(version));

            System.out.printf("successfully installed Tomcat %s%n", ctx.var(version));

            return new TaskResult(r);
        }
    };


    public final DynamicVariable<String[]> warCacheDirs = dynamic(new VarFun<String[]>() {
        public String[] apply() {
            final String name = FilenameUtils.getBaseName(ctx.var(warName));
            return new String[]{
                ctx.system.joinPath(ctx.var(webapps), name)
            };
        }
    });
}
