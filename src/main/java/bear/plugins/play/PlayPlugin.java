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
import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.console.ConsoleCallbackResultType;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.main.event.NoticeEventToUI;
import bear.plugins.ZippedToolPlugin;
import bear.plugins.java.JavaPlugin;
import bear.plugins.misc.*;
import bear.plugins.sh.SystemSession;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bear.session.Variables.*;
import static com.google.common.base.Predicates.containsPattern;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.tryFind;
import static java.lang.String.format;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PlayPlugin extends ZippedToolPlugin {
    private static final Logger logger = LoggerFactory.getLogger(PlayPlugin.class);

    protected UpstartPlugin upstart;
    protected FileWatchDogPlugin watchDog;

    public final DynamicVariable<String>
        projectPath = dynamic("Project root dir"),
        configFile = dynamic("Path to an alternative application.conf"),
        execName = concat("play", condition(bear.isNativeUnix, newVar(""), newVar(".bat")).desc("'play' or 'play.bat'")),
        stageTargetPattern = concat(projectPath, "/target/universal/stage/bin"),
        instancePath = concat(bear.applicationPath, "/instances/play-%s"),
        instanceLogsPath = concat(bear.appLogsPath, "/play-%s"),
        consoleLogPath = concat(instanceLogsPath, "/console"),
        instancePorts = newVar("9000"),
        multiServiceName = concat(bear.name, "_%s"),
        singleServiceName = equalTo(bear.name),
        groupName = equalTo(bear.name);


    public final DynamicVariable<WatchDogGroup>
        watchStartDogGroup = newVar(null);

    public final DynamicVariable<Integer>
        startupTimeoutMs = equalTo(bear.buildTimeoutMs);

    public final DynamicVariable<Boolean>
        useWatchDog = newVar(true);

    public final DynamicVariable<List<String>> portsSplit = split(instancePorts, COMMA_SPLITTER);


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
                        .timeoutForBuild()
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

    public final TaskDef<Task> build = new TaskDef<Task>() {
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
//                        .timeoutForBuild()
//                        .build()
                            .run();

                    return result;
                }
            };
        }
    };

    public final TaskDef<Task> start = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("starting the app (stage)...");

                    String capture = $.sys.capture("ls -w 1 " + $(stageTargetPattern) + "/*");

                    Optional<String> execPath = tryFind(LINE_SPLITTER.split(
                        capture.trim()), not(containsPattern("\\.bat$")));

                    if (!execPath.isPresent()) {
                        throw new RuntimeException("could not find a jar, dir content: " + capture);
                    }

                    Script script = new Script($.sys);

                    final List<String> ports = $(portsSplit);

                    boolean single = ports.size() == 1;

                    List<UpstartService> serviceList = new ArrayList<UpstartService>(ports.size());

                    TaskResult r;

                    for (String port : ports) {
                        $.sys.mkdirs(instancePath(port), format($(instanceLogsPath), port));

                        String consolePath = consoleLogPath(port);

                        script.line().addRaw("exec " + execPath.get() + " -Dhttp.port=" + port + " 2>&1 >" + consolePath).build();

                        serviceList.add(new UpstartService(
                            single ? $(singleServiceName) : format($(multiServiceName), port),
                            $(bear.fullName),
                            removeLastSemiColon(script.asTextScript())
                        ).cd(instancePath(port)));
                    }

                    UpstartServices services = new UpstartServices(
                        single ? Optional.<String>absent() : Optional.of($(groupName)),
                        serviceList
                    );

                    r = $.runSession(
                        upstart.create.singleTask().createNewSession($, parent),
                        services);

                    if (!r.ok()) return r;

                    if ($(useWatchDog)) {
                        spawnStartWatchDogs(ports);
                    }

                    r = serviceCommand($, "start");

                    return r;
                }

                private void spawnStartWatchDogs(List<String> ports) {
                    final WatchDogGroup compositeRunnable = new WatchDogGroup(ports.size(), watchStartDogGroup );

                    for (final String port : ports) {
                        WatchDogRunnable runnable = new WatchDogRunnable($, watchDog, new WatchDogInput(
                            consoleLogPath(port), false, new ConsoleCallback() {
                            @Override
                            @Nonnull
                            public ConsoleCallbackResult progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                                if (buffer.contains("Listening for HTTP on")) {
                                    SessionContext.ui.info(new NoticeEventToUI($(bear.fullName), "started play instance on port " + port));

                                    return new ConsoleCallbackResult(ConsoleCallbackResultType.DONE, null);
                                }

                                return ConsoleCallbackResult.CONTINUE;
                            }
                        })
                            .setTimeoutMs($(startupTimeoutMs))
                        );

                        compositeRunnable.add(runnable);
                    }

                    compositeRunnable.startThreads();

                    //this should not be needed
                    $.getGlobal().scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            compositeRunnable.shutdownNow();
                        }
                    }, $(startupTimeoutMs), TimeUnit.MILLISECONDS);
                }


                private String instancePath(String port) {
                    return format($(instancePath), port);
                }

                private String consoleLogPath(String port) {
                    return format($(consoleLogPath), port);
                }
            };
        }
    };


    private String removeLastSemiColon(String s) {
        if (s.endsWith(";")) {
            return s.substring(0, s.length() - 1);
        }

        return s;
    }

    private CommandLineResult serviceCommand(SessionContext $, String command) {
        boolean isSingle = $.var(portsSplit).size() == 1;
        SystemSession.OSHelper helper = $.sys.getOsInfo().getHelper();

        return $.sys.captureResult(
            helper.serviceCommand($.var(isSingle ? singleServiceName : multiServiceName), command), true);
    }

    public final TaskDef<Task> stop = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return new Task<TaskDef>(parent, this, $) {

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("stopping the app (stage)...");

                    CommandLineResult r;

                    try {
                        r = serviceCommand($, "stop");

                        if (!r.ok()) {
                            logger.warn("unable to stop service: {}", r);
                        }
                    } catch (Exception e) {
                        logger.warn("unable to stop service: ", e);
                    }

                    return TaskResult.OK;
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
    };

    @Override
    public DependencyResult checkPluginDependencies() {
        return require(JavaPlugin.class);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }

    public final TaskDef<Task> watchStart = new TaskDef<Task>() {
        @Override
        protected Task newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, new TaskCallable() {
                @Override
                public TaskResult call(SessionContext $, Task task, Object input) throws Exception {
                    if (!$.var(useWatchDog)) return TaskResult.OK;

                    WatchDogGroup watchDogs = $.var(watchStartDogGroup);

                    //removed due to timeout, this should be very rare if not impossible
                    if (watchDogs == null) return TaskResult.OK;

                    return TaskResult.of(watchDogs.latch().await($.var(startupTimeoutMs), TimeUnit.MILLISECONDS),
                        "" + watchDogs.latch().getCount() + " instances did not start in " +
                            TimeUnit.MILLISECONDS.toSeconds($.var(startupTimeoutMs)) + " seconds");
                }
            });
        }
    };

}
