package cap4j.plugins.java;

import cap4j.core.GlobalContext;
import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;
import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import cap4j.session.VariableUtils;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;

import java.io.File;

import static cap4j.core.CapConstants.newVar;
import static cap4j.core.CapConstants.strVar;

/**
 * User: achaschev
 * Date: 8/4/13
 */
public class JavaPlugin extends Plugin {
    public DynamicVariable<String>

    homePath = newVar("/var/lib/java"),

    javaSharedDirPath,
    javaSharedBuildDirPath,
    javaLinuxDistributionName = strVar(),
    javaWindowsDistributionName = strVar(),
    javaLinuxDistributionPath,
    javaWindowsDistributionPath,
    javaDistributionName,
    javaDistributionPath
    ;

    public final Task setup = new Task("setup java") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.rm(ctx.var(javaSharedBuildDirPath));
            system.mkdirs(ctx.var(javaSharedBuildDirPath));

            final File localDFile = new File(global.localCtx.var(
                system.isNativeUnix() ? javaLinuxDistributionPath : javaWindowsDistributionPath));

            if(!localDFile.exists()){
                throw new RuntimeException("expecting java distribution at " + localDFile.getAbsolutePath());
            }

            system.upload(ctx.var(javaDistributionPath), localDFile);

            final String distrName = ctx.var(javaDistributionName);
            if(distrName.endsWith("gz")) {
                system.run(
                    system.newCommandLine()
                        .timeoutSec(30)
                        .cd(ctx.var(javaSharedBuildDirPath))
                        .addRaw("tar xvf").a(distrName)
                );
            }else{
                system.script()
                        .cd(ctx.var(javaSharedBuildDirPath))
                        .line().addRaw("chmod u+x %s", distrName).build()
                        .line()
                            .timeoutSec(30)
                            .addRaw("./%s", distrName).build()
                    .run();
            }

            String jdkDirName = system.capture(String.format("cd %s && ls -w 1 | grep -v gz | grep -v bin", ctx.var(javaSharedBuildDirPath))).trim();

            system.run(system.script()
                .line().sudo().addRaw("rm -r /var/lib/java").build()
                .line().sudo().addRaw("rm -r /var/lib/jdks/%s", jdkDirName).build()
                .line().sudo().addRaw("mkdir -p /var/lib/jdks").build()
                .line().sudo().addRaw("mv %s/%s /var/lib/jdks", ctx.var(javaSharedBuildDirPath), jdkDirName).build()
                .line().sudo().addRaw("ln -s /var/lib/jdks/%s /var/lib/java", jdkDirName).build()
                .line().sudo().addRaw("chmod -R g+r,o+r /var/lib/java").build()
                .line().sudo().addRaw("chmod u+x,g+x,o+x /var/lib/java/bin/*").build()
                .line().sudo().addRaw("rm /usr/bin/java").build()
                .line().sudo().addRaw("ln -s /var/lib/java/bin/java /usr/bin/java").build()
                .line().sudo().addRaw("rm /usr/bin/javac").build()
                .line().sudo().addRaw("ln -s /var/lib/java/bin/javac /usr/bin/javac").build(),
                SystemEnvironment.passwordCallback(ctx.var(cap.sshPassword))
            );

            return new TaskResult(Result.OK);
        }
    };


    public JavaPlugin(GlobalContext global) {
        super(global);
        javaSharedDirPath = VariableUtils.joinPath(cap.sharedPath, "java");
        javaSharedBuildDirPath = VariableUtils.joinPath(javaSharedDirPath, "build");
        javaLinuxDistributionPath = VariableUtils.joinPath(javaSharedBuildDirPath, javaLinuxDistributionName);
        javaWindowsDistributionPath = VariableUtils.joinPath(javaSharedBuildDirPath, javaWindowsDistributionName);
        javaDistributionName = VariableUtils.condition(cap.isNativeUnix, javaLinuxDistributionName, javaLinuxDistributionName);
        javaDistributionPath = VariableUtils.condition(cap.isNativeUnix, javaLinuxDistributionPath, javaWindowsDistributionPath);

    }
}
