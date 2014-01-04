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

package bear.plugins.grails;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.ServerToolPlugin;
import bear.plugins.java.JavaPlugin;
import bear.plugins.misc.PendingRelease;
import bear.plugins.misc.ReleasesPlugin;
import bear.plugins.sh.CommandLine;
import bear.plugins.sh.Script;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GrailsPlugin2 extends ServerToolPlugin {
    JavaPlugin java;
    ReleasesPlugin releases;

    public final DynamicVariable<String>
        warName = newVar("ROOT.war").desc("i.e. ROOT.war");

    public final DynamicVariable<Boolean>
        clean = Variables.equalTo(bear.clean).desc("clean project before build")
    ;

    public GrailsPlugin2(GlobalContext global) {
        super(global);

        toolname.defaultTo("grails", true);
        distrFilename.setEqualTo(concat(versionName, ".zip"));
        distrWwwAddress.setEqualTo(format("http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/%s", distrFilename));
        instancePorts.defaultTo("8080");
        useUpstart.defaultTo(false);

//        releaseWarPath = condition(bear.isRemoteEnv, joinPath(releases.releasePath, warName), projectWarPath);
    }

    public final TaskDef<Object, TaskResult<?>> build = new TaskDef<Object, TaskResult<?>>(new NamedSupplier<Object, TaskResult<?>>("grails.build", new SingleTaskSupplier<Object, TaskResult<?>>() {
        @Override
        public Task<Object, TaskResult<?>> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<Object, TaskResult<?>> def) {
            return new Task<Object, TaskResult<?>>(parent, def, $) {

                @Override
                protected TaskResult<?> exec(SessionRunner runner) {
                    $.log("building the Grails project ...");

                    PendingRelease pendingRelease = $.var(releases.pendingRelease);

                    final Script script = $.sys.script().cd($(projectPath));

                    if ($(clean)) {
                        script.add(grails($).a("clean"));
                    }

                    String warPath = pendingRelease.path + "/" + $(warName);

                    CommandLineResult<?> buildResult = script
                        .add(grails($).addRaw("war").a(warPath))
                        .run();

                    if(buildResult.output.contains("Use --stacktrace to see the full trace") ||
                        buildResult.output.contains("Set log level to 'warn")){
                        buildResult.setError();
                    }

//                    CommandLineResult buildResult = CommandLineResult.OK;

                    //todo extract the error text (i.e. '8 compilation errors')

                    return new GrailsBuildResult(buildResult.getResult(), warPath);
                }
            };
        }
    }));

    private CommandLine grails(SessionContext $) {
        return $.sys.line()
            .setVar("JAVA_HOME", $.var(java.homePath))
            .a($.var(execPath))
            .timeoutForBuild();
    }

    @Override
    protected void spawnStartWatchDogs(SessionContext $, List<String> ports) {
        throw new UnsupportedOperationException("todo GrailsPlugin2.spawnStartWatchDogs");
    }

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>(new SingleTaskSupplier<Object, TaskResult<?>>(){

        @Override
        public Task<Object, TaskResult<?>> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<Object, TaskResult<?>> def) {
            return new ZippedTool(parent, (InstallationTaskDef) def, $) {
                @Override
                protected TaskResult<?> exec(SessionRunner runner) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut("grails", "bin/grails");
                    shortCut("startGrails", "bin/startGrails");

                    return verify();
                }

                @Override
                protected String extractVersion(String output) {
                    return StringUtils.substringAfter(output, "version: ").trim();
                }

                @Override
                protected String createVersionCommandLine() {
                    return "grails --version";
                }
            };
        }
    });

    @Override
    public DependencyResult checkPluginDependencies() {
        return require(JavaPlugin.class);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }
}
