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
import bear.core.BearScript2;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.ZippedToolPlugin;
import bear.plugins.java.JavaPlugin;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;

import static bear.session.Variables.*;
import static com.google.common.base.Predicates.containsPattern;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.tryFind;
import static java.lang.String.format;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PlayPlugin extends ZippedToolPlugin {
    public final DynamicVariable<String>
        projectPath = dynamic("Project root dir"),
        configFile = dynamic("Path to an alternative application.conf"),
        execName = concat("play", condition(bear.isNativeUnix, newVar(""), newVar(".bat")).desc("'play' or 'play.bat'")),
        stageTargetPattern = concat(projectPath, "/target/universal/stage/bin"),
        instancePath = concat(bear.applicationPath, "/instances/play-%s"),
        instancePorts = newVar("9000");

    public final DynamicVariable<Boolean>
        clean = Variables.equalTo(bear.clean).desc("clean project before build");

    public PlayPlugin(GlobalContext global) {
        super(global);

        toolname.defaultTo("play", true);
        distrFilename.setEqualTo(concat(versionName, ".zip"));
        distrWwwAddress.setEqualTo(Variables.format("http://downloads.typesafe.com/play/%s/%s", version, distrFilename));
    }

    public static class PlayDistResult extends TaskResult {
        String distPath;

        public PlayDistResult(String distPath) {
            super(Result.OK);
            this.distPath = distPath;
        }

    }

    public final TaskDef<Task> dist = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $) {

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    final Script script = new Script($.sys)
                        .cd($(projectPath));

                    CommandLine line = script
                        .line().addRaw("play")
                        .timeoutMin(2);

                    if ($.isSet(configFile)) {
                        line.a("-Dconfig.file=" + $(configFile));
                    }

                    line.addRaw("dist");

                    CommandLineResult result = line
                        .build()
                        .run();

                    if (!result.ok()) {
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
            return new Task<TaskDef>(parent, this, $) {

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("building the project (stage)...");

//                    $.sys.rm($(stageTargetPattern) + "/*");

                    CommandLineResult result;

                    Script script = new Script($.sys)
                        .cd($(projectPath));

                    if ($(clean)) {
                        script.line().addRaw("play clean-all");
                    }

                    result =
                        script
//                        .line().addRaw("play compile stage")
//                        .timeoutMin(10)
//                        .build()
                        .run();

                    return result;
                }
            };
        }
    };

    public final TaskDef<Task> stageStart = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $) {

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("starting the app (stage)...");

                    String capture = $.sys.capture("ls -w 1 " + $(stageTargetPattern) + "/*");

                    Optional<String> execPath = tryFind(BearScript2.LINE_SPLITTER.split(
                        capture.trim()), not(containsPattern("\\.bat$")));

                    if (!execPath.isPresent()) {
                        throw new RuntimeException("could not find a jar, dir content: " + capture);
                    }

                    Script script = new Script($.sys);
                    CommandLineResult result;

                    for (String port : $(instancePorts).split(",")) {
                        $.sys.addRmLine(script.line(), instancePath(port) + "/output");

                        script
                            .line().addRaw("mkdir -p %s", instancePath(port)).build()
                            .line()
                            .cd(instancePath(port))
                            .addRaw("nohup " + execPath.get() + " -Dhttp.port="+ port + " &")
                            .timeoutSec(4)
                            .build();
                    }

                    result = script.run();

                    return result;
                }

                private String instancePath(String port) {
                    return format($(instancePath), port);
                }
            };
        }
    };



    public final TaskDef<Task> stageStop = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $) {

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
                    return StringUtils.substringBetween(
                        output,
                        "play ", " built").trim();
                }

                @Override
                protected String createVersionCommandLine() {
                    return "play help";
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
