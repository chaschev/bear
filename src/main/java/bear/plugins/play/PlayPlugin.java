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

package bear.plugins.play;

import bear.cli.CommandLine;
import bear.cli.Script;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.ZippedToolPlugin;
import bear.plugins.java.JavaPlugin;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import org.apache.commons.lang3.StringUtils;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PlayPlugin extends ZippedToolPlugin {
    public final DynamicVariable<String>
        grailsBin = joinPath(homePath, "bin"),
        projectPath = dynamic("Project root dir"),
        configFile = dynamic("Path to an alternative application.conf"),
        execName = concat("play", condition(bear.isNativeUnix, newVar(""), newVar(".bat")).desc("'play' or 'play.bat'")),
        execPath = condition(isSet(homePath), joinPath(grailsBin, execName), execName)
            ;

    public final DynamicVariable<Boolean>
        clean = Variables.equalTo(bear.clean).desc("clean project before build")
        ;

    public PlayPlugin(GlobalContext global) {
        super(global);

        toolname.defaultTo("play", true);
        distrFilename.setEqualTo(concat(versionName, ".zip"));
        distrWwwAddress.setEqualTo(format("http://downloads.typesafe.com/play/%s/%s.zip", version, distrFilename));
    }

    public static class PlayDistResult extends TaskResult{
        String distPath;

        public PlayDistResult(String distPath) {
            super(Result.OK);
            this.distPath = distPath;
        }

    }

    public final TaskDef<Task> dist = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $){

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    final Script script = new Script($.sys)
                        .cd($(projectPath));

                    CommandLine line = script
                        .line().addRaw("play")
                        .timeoutMin(2);

                    if($.isSet(configFile)){
                        line.a("-Dconfig.file=" + $(configFile));
                    }

                    line.addRaw("dist");

                    CommandLineResult result = line
                        .build()
                        .run();

                    if(!result.ok()){
                        return result;
                    }

                    String path = StringUtils.substringBetween(result.text, "ready in ", "\n");

                    return new PlayDistResult(path);
                }
            };
        }
    };

    public final TaskDef<Task> stage = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $){

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("building the project (stage)...");

                    CommandLineResult result;

                    result = new Script($.sys)
                        .cd($(projectPath))
                        .line().addRaw(String.format("play %scompile stage", $(clean) ? "clean " : ""))
                        .timeoutMin(2)
                        .build()
                        .run();

                    return result;
                }
            };
        }
    };

    public final TaskDef<Task> stageStart = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $){

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("starting the app (stage)...");

                    CommandLineResult result;

                    result = new Script($.sys)
                        .cd($(projectPath))
                        .line().addRaw("target/start")
                        .timeoutSec(20)
                        .build()
                        .run();

                    return result;
                }
            };
        }
    };

    public final TaskDef<Task> stageStop = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $){

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("stopping the app (stage)...");

                    CommandLineResult result;

                    result = new Script($.sys)
                        .cd($(projectPath))
                        .line().addRaw("kill play")
                        .timeoutSec(10)
                        .build()
                        .run();

                    return result;
                }
            };
        }
    };



    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>() {
        @Override
        public ZippedTool newSession(SessionContext $, final Task parent) {
            return new ZippedTool(parent, this, $) {
                @Override
                protected DependencyResult exec(SessionTaskRunner runner, Object input) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut("play", "play");

                    return verify();
                }

                @Override
                protected String extractVersion(String output) {
                    return StringUtils.substringAfter(
                        output,
                        "version: ").trim();
                }

                @Override
                protected String createVersionCommandLine() {
                    return "play --version";
                }
            };
        }
    };

    @Override
    public DependencyResult checkPluginDependencies() {
        return require(JavaPlugin.class);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }
}
