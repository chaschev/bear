/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cap4j.plugins.java;

import cap4j.core.Dependency;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;
import cap4j.session.SystemEnvironment;
import cap4j.session.Variables;
import cap4j.task.*;

import java.io.File;

import static cap4j.session.Variables.newVar;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class JavaPlugin extends Plugin {
    public DynamicVariable<String>

        homePath = newVar("/var/lib/java"),

    javaSharedDirPath,
        javaSharedBuildDirPath,
        javaLinuxDistributionName = Variables.strVar(),
        javaWindowsDistributionName = Variables.strVar(),
        javaLinuxDistributionPath,
        javaWindowsDistributionPath,
        javaDistributionName,
        javaDistributionPath;

    public final InstallationTaskDef<InstallationTask> install = new InstallationTaskDef<InstallationTask>() {
        @Override
        public InstallationTask newSession(SessionContext $) {
            return new InstallationTask(this, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    $.sys.rm($(javaSharedBuildDirPath));
                    $.sys.mkdirs($(javaSharedBuildDirPath));

                    final File localDFile = new File(global.localCtx.var(
                        $.sys.isNativeUnix() ? javaLinuxDistributionPath : javaWindowsDistributionPath));

                    if (!localDFile.exists()) {
                        throw new RuntimeException("expecting java distribution at " + localDFile.getAbsolutePath());
                    }

                    $.sys.upload($(javaDistributionPath), localDFile);

                    final String distrName = $(javaDistributionName);
                    if (distrName.endsWith("gz")) {
                        $.sys.run(
                            $.sys.line()
                                .timeoutSec(30)
                                .cd($(javaSharedBuildDirPath))
                                .addRaw("tar xvf").a(distrName)
                        );
                    } else {
                        $.sys.script()
                            .cd($(javaSharedBuildDirPath))
                            .line().addRaw("chmod u+x %s", distrName).build()
                            .line()
                            .timeoutSec(30)
                            .addRaw("./%s", distrName).build()
                            .run();
                    }

                    String jdkDirName = $.sys.capture(String.format("cd %s && ls -w 1 | grep -v gz | grep -v bin", $(javaSharedBuildDirPath))).trim();

                    $.sys.run($.sys.script()
                        .line().sudo().addRaw("rm -r /var/lib/java").build()
                        .line().sudo().addRaw("rm -r /var/lib/jdks/%s", jdkDirName).build()
                        .line().sudo().addRaw("mkdir -p /var/lib/jdks").build()
                        .line().sudo().addRaw("mv %s/%s /var/lib/jdks", $(javaSharedBuildDirPath), jdkDirName).build()
                        .line().sudo().addRaw("ln -s /var/lib/jdks/%s /var/lib/java", jdkDirName).build()
                        .line().sudo().addRaw("chmod -R g+r,o+r /var/lib/java").build()
                        .line().sudo().addRaw("chmod u+x,g+x,o+x /var/lib/java/bin/*").build()
                        .line().sudo().addRaw("rm /usr/bin/java").build()
                        .line().sudo().addRaw("ln -s /var/lib/java/bin/java /usr/bin/java").build()
                        .line().sudo().addRaw("rm /usr/bin/javac").build()
                        .line().sudo().addRaw("ln -s /var/lib/java/bin/javac /usr/bin/javac").build(),
                        SystemEnvironment.passwordCallback($(cap.sshPassword))
                    );

                    return TaskResult.OK;
                }


                @Override
                public Dependency asInstalledDependency() {
                    return Dependency.NONE;
                }
            };

        }


    };


    public JavaPlugin(GlobalContext global) {
        super(global);
        javaSharedDirPath = Variables.joinPath(cap.sharedPath, "java");
        javaSharedBuildDirPath = Variables.joinPath(javaSharedDirPath, "build");
        javaLinuxDistributionPath = Variables.joinPath(javaSharedBuildDirPath, javaLinuxDistributionName);
        javaWindowsDistributionPath = Variables.joinPath(javaSharedBuildDirPath, javaWindowsDistributionName);
        javaDistributionName = Variables.condition(cap.isNativeUnix, javaLinuxDistributionName, javaLinuxDistributionName);
        javaDistributionPath = Variables.condition(cap.isNativeUnix, javaLinuxDistributionPath, javaWindowsDistributionPath);

    }


    @Override
    public InstallationTaskDef<InstallationTask> getInstall() {
        return install;
    }
}
