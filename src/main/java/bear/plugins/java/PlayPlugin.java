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

package bear.plugins.java;

import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.ServerToolPlugin;
import bear.plugins.misc.*;
import bear.plugins.sh.Script;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bear.session.Variables.*;
import static com.google.common.base.Predicates.containsPattern;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.tryFind;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PlayPlugin extends ServerToolPlugin {
    public static final Logger logger = LoggerFactory.getLogger(PlayPlugin.class);

    public final DynamicVariable<String>
        configFile = dynamic("Path to an alternative application.conf"),
        stageTarget = concat(projectPath, "/target/universal/stage"),
        consoleLogPath = concat(instanceLogsPath, "/console"),
        releaseExecPath = undefined("a path to a play executable to run, internal var")
            ;


    public PlayPlugin(GlobalContext global) {
        super(global);

        toolname.defaultTo("play", true);
        distrFilename.setEqualTo(concat(versionName, ".zip").temp());
        distrWwwAddress.setEqualTo(Variables.format("http://downloads.typesafe.com/play/%s/%s", version, distrFilename));

        createScriptText.setDynamic(new Fun<SessionContext, Function<String, String>>() {
            @Override
            public Function<String, String> apply(final SessionContext $) {
                return new Function<String, String>() {
                    @Override
                    public String apply( String port) {
                        String consolePath = consoleLogPath(port, $);

                        // good thing to do before any restart
                        resetConsolePath($, consolePath);

                        return $.sys.script().line().addRaw("" +
                            "exec " + $.var(releaseExecPath) + " -Dhttp.port=" + port + " 2>&1 >" + consolePath).build().asTextScript();
                    }
                };
            }
        });

        start.addBeforeTask(new TaskDef<Task>(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                Optional<Release> active = $.var(releases.activatedRelease);

                if(active.isPresent()){
                    Optional<String> execPath = getExecPath($, active.get());

                    if(!execPath.isPresent()){
                        return TaskResult.error("no executable for play release: " + active.get().toString());
                    }

                    $.putConst(releaseExecPath, execPath.get());
                }else{
                    return TaskResult.error("no active release found");
                }

                return TaskResult.OK;
            }
        }).setName("play - find exec path"));
    }

    private Optional<String> getExecPath(SessionContext $, Release release) {
        String capture = $.sys.capture("ls -w 1 " + release.path + "/" + $.var(appName) + "/bin/*");

        Optional<String> execPath = tryFind(LINE_SPLITTER.split(
            capture.trim()), not(containsPattern("\\.bat$")));

        if (!execPath.isPresent()) {
            throw new RuntimeException("could not find a jar, dir content: " + capture);
        }

        return execPath;
    }

    public final TaskDef<Task> build = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    $.log("building the project (stage)...");

                    CommandLineResult result;

                    Script script = new Script($.sys)
                        .cd($(projectPath));

                    if ($(clean)) {
                        script.line().addRaw("play clean-all");
                    }

                    result =
                        script
                            .line().addRaw("play compile stage")
                            .timeoutForBuild()
                            .build()
                            .run();

                    if (!result.ok()) {
                        return result;
                    }

                    PendingRelease pendingRelease = $.var(releases.pendingRelease);

                    String dest = pendingRelease.path + "/" + $(appName); //eq to ${pendingRelease.path}/play-app

                    $.sys.mkdirs(dest).run();
                    //TODO FIX
                    $.sys.captureBuilder("mv " + $(stageTarget) + "/* " + dest).run().throwIfError();

                    return result;
                }
            };
        }
    });



    private void printCurrentReleases(SessionContext $) {
        logger.info("current releases:\n{}", $.var(releases.session).show());
    }

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>(new SingleTaskSupplier() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new ZippedTool(parent, (InstallationTaskDef) def, $) {
                @Override
                protected DependencyResult exec(SessionRunner runner, Object input) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut($(execName), $(execName));

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
    });

    @Override
    public DependencyResult checkPluginDependencies() {
        return require(JavaPlugin.class);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }

    @Override
    protected void spawnStartWatchDogs(final SessionContext $, List<String> ports) {
        final WatchDogGroup watchDogGroup = new WatchDogGroup(ports.size(), watchStartDogGroup);

        for (final String port : ports) {
            String consolePath = consoleLogPath(port, $);

            // to make sure there are no old start entries
            resetConsolePath($, consolePath);

            WatchDogRunnable runnable = new WatchDogRunnable($, watchDog, new WatchDogInput(
                consolePath, false, new ConsoleCallback() {
                @Override
                @Nonnull
                public ConsoleCallbackResult progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                    if (buffer.contains("Listening for HTTP on")) {
                        return startedResult($, port);
                    }

                    if(buffer.contains("Oops, cannot start the server.")){
                        return notStartedResult($, port);
                    }

                    return ConsoleCallbackResult.CONTINUE;
                }
            })
                .setTimeoutMs($.var(startupTimeoutMs))
            );

            watchDogGroup.add(runnable);
        }

        watchDogGroup.startThreads();

        watchDogGroup.scheduleForcedShutdown($.getGlobal().scheduler, $.var(bear.appStartTimeoutSec), TimeUnit.SECONDS);
    }
}
