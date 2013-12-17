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

package bear.plugins;

import bear.console.AbstractConsole;
import bear.console.ConsoleCallbackResult;
import bear.console.ConsoleCallbackResultType;
import bear.context.Fun;
import bear.core.Env;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.main.event.NoticeEventToUI;
import bear.plugins.misc.*;
import bear.plugins.nodejs.NodeJsPlugin;
import bear.plugins.sh.SystemSession;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bear.core.SessionContext.ui;
import static bear.session.Variables.*;
import static java.lang.String.format;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class ServerToolPlugin extends ZippedToolPlugin {
    public static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(NodeJsPlugin.class);

    protected UpstartPlugin upstart;
    protected FileWatchDogPlugin watchDog;
    protected ReleasesPlugin releases;

    public final DynamicVariable<String>
        projectPath = equalTo(bear.vcsBranchLocalPath).desc("Project root dir"),
        execName = concat(toolname, condition(bear.isNativeUnix, newVar("").temp(), newVar(".bat").temp()).desc("'play' or 'play.bat'")),
        instancePath = undefined(),
        instanceLogsPath = concat(bear.appLogsPath, "/", toolname, "-%s"),
        appName = concat(toolname, "-app"),
        instancePorts = newVar("9000"),
        user = equalTo(bear.sshUsername),
        group = equalTo(bear.sshUsername),
        userWithGroup = concat(user, ".", group),
        multiServiceName = concat(bear.name, "_%s"),
        singleServiceName = equalTo(bear.name),
        groupName = equalTo(bear.name),
        envString = newVar("production"),
        consoleLogPath = concat(instanceLogsPath, "/", envString, ".log"),
        execPath = equalTo(toolname);

    public final DynamicVariable<Env> env = convert(envString, toEnum(Env.class));

    public final DynamicVariable<Boolean> useUpstart = newVar(true);

    public final DynamicVariable<Function<ConfigureServiceInput, Void>> configureService = dynamic(new Fun<SessionContext, Function<ConfigureServiceInput, Void>>() {
        @Override
        public Function<ConfigureServiceInput, Void> apply(final SessionContext $) {
            return null;
        }
    });

    public final DynamicVariable<Function<String, String>> createScriptText = undefined();

    public final DynamicVariable<UpstartServices> customUpstart = dynamic(new Fun<SessionContext, UpstartServices>() {
        @Override
        public UpstartServices apply(SessionContext $) {
            List<String> ports = $.var(portsSplit);

            boolean single = ports.size() == 1;

            List<UpstartService> serviceList = new ArrayList<UpstartService>(ports.size());

            for (String port : ports) {
                $.sys.mkdirs(instancePath(port, $), format($.var(instanceLogsPath), port));

                String scriptText = $.var(createScriptText).apply(port);

                UpstartService upstartService = new UpstartService(
                    single ? $.var(singleServiceName) : format($.var(multiServiceName), port),
                    $.var(bear.fullName),
                    scriptText
                ).setUser($.var(user));

                serviceList.add(upstartService.cd(instancePath(port, $)));

                $.var(configureService).apply(new ConfigureServiceInput(port, upstartService));
            }

            return new UpstartServices(
                single ? Optional.<String>absent() : Optional.of($.var(groupName)),
                serviceList
            );
        }
    });

    public final DynamicVariable<WatchDogGroup>
        watchStartDogGroup = newVar(null);

    public final DynamicVariable<Integer>
        startupTimeoutMs = equalTo(bear.buildTimeoutMs);

    public final DynamicVariable<Boolean>
        useWatchDog = newVar(true);

    public final DynamicVariable<List<String>> portsSplit = split(instancePorts, COMMA_SPLITTER);


    public final DynamicVariable<Boolean>
        clean = Variables.equalTo(bear.clean).desc("clean project before build");

    public ServerToolPlugin(GlobalContext global) {
        super(global);
    }

    @Override
    public void initPlugin() {
        super.initPlugin();
        instancePath.setEqualTo(concat(releases.currentReleaseLinkPath, "/instances/", toolname, "-%s"));
    }

    public void resetConsolePath(SessionContext $, String logPath) {
        $.sys.chmod("u+rwx,g+rwx,o+rwx", false, logPath);
        $.sys.resetFile(logPath, true);
    }

    protected CommandLineResult serviceCommand(SessionContext $, String command) {
        List<String> ports = $.var(portsSplit);
        boolean isSingle = ports.size() == 1;
        SystemSession.OSHelper helper = $.sys.getOsInfo().getHelper();

        for (String port : ports) {
            $.sys.captureResult(
                helper.serviceCommand(format($.var(isSingle ? singleServiceName : multiServiceName), port), command), true)
                .throwIfError();
        }

        return CommandLineResult.OK;
    }

    public final TaskDef<Task> start = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, final Task parent, final TaskDef<Task> def) {
            return new Task<TaskDef>(parent, def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    $.log("starting the {} app...", $(toolname));

                    Optional<Release> optionalRelease = $.var(releases.activatedRelease);

                    if (!optionalRelease.isPresent()) {
                        return TaskResult.error("there is no release to start!");
                    }

                    TaskResult r;

                    //bear.installationInProgress is for tomcat
                    //useUpstart is for other plugins
                    if ($(bear.installationInProgress) || $(useUpstart)) {
                        UpstartServices services = $(customUpstart);

                        r = $.runSession(
                            upstart.create.singleTaskSupplier().createNewSession($, parent, def),
                            services);

                        if (!r.ok()) return r;
                    }

                    if ($(useWatchDog)) {
                        spawnStartWatchDogs($, $(portsSplit));
                    }

                    r = serviceCommand($, "start");

                    printCurrentReleases($);

                    return r;
                }
            };
        }
    });

    public String instancePath(String port, SessionContext $) {
        return path(instancePath, port, $);
    }

    public String path(DynamicVariable<String> pathVar, String port, SessionContext $) {
        return format($.var(pathVar), port);
    }

    // todo extract common things
    // from this abstract method

    protected abstract void spawnStartWatchDogs(final SessionContext $, List<String> ports);

    public final TaskDef<Task> stop = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, def, $) {

                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
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
    });

    public final TaskDef<Task> watchStart = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, new TaskCallable<TaskDef>() {
                @Override
                public TaskResult call(SessionContext $, Task task, Object input) throws Exception {
                    TaskResult r;
                    if (!$.var(useWatchDog)) {
                        r = TaskResult.OK;
                    } else {
                        WatchDogGroup watchDogGroup = $.var(watchStartDogGroup);

                        boolean awaitOk = watchDogGroup.latch().await($.var(startupTimeoutMs), TimeUnit.MILLISECONDS);

                        if (awaitOk) {
                            r = watchDogGroup.getResult();
                        } else {
                            r = TaskResult.error(watchDogGroup.latch().getCount() + " instances did not start in " +
                                TimeUnit.MILLISECONDS.toSeconds($.var(startupTimeoutMs)) + " seconds");
                        }
                    }

                    printCurrentReleases($);

                    return r;
                }
            });
        }
    });

    private void printCurrentReleases(SessionContext $) {
        logger.info("current releases:\n{}", $.var(releases.session).show());
    }

    public String consoleLogPath(String port, SessionContext $) {
        return format($.var(consoleLogPath), port);
    }

    protected NoticeEventToUI newNotice(String message, SessionContext $) {
        return new NoticeEventToUI($.var(bear.fullName), message);
    }

    protected String newStartedMessage(SessionContext $, String port) {
        return "started instance at " +
            $.getName() +":" + port + ", release " + ", release " + $.var(releases.session).getCurrentRelease().get().name();
    }

    protected String sendMessage(Level level, String message, SessionContext $) {
        logger.log(level, message);
        ui.log(level, newNotice(message, $));

        return message;
    }

    protected String newSeemToHaveStartedMessage(SessionContext $, String port) {
        return "seem to have started after timeout, node instance at " +
            $.getName() + ":" + port + ", release " + $.var(releases.session).getCurrentRelease().get().name();
    }

    protected String newCantStartMessage(SessionContext $, String port) {
        return "unable to start instance at " +
            $.getName() +":" + port + ", release " + $.var(releases.session).getCurrentRelease().get().name();
    }

    protected ConsoleCallbackResult notStartedResult(SessionContext $, String port) {
        return new ConsoleCallbackResult(ConsoleCallbackResultType.EXCEPTION, sendMessage(Level.ERROR, newCantStartMessage($, port), $));
    }

    protected void seemsHaveStarted(final AbstractConsole.Terminal console, final SessionContext $, final String port) {
        $.getGlobal().scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if(!console.isDone()){
                    console.finishWithResult(new ConsoleCallbackResult(ConsoleCallbackResultType.DONE,
                        sendMessage(Level.INFO, newSeemToHaveStartedMessage($, port), $)));
                }
            }
        }, 5, TimeUnit.SECONDS);
    }

    protected ConsoleCallbackResult startedResult(SessionContext $, String port) {
        return new ConsoleCallbackResult(ConsoleCallbackResultType.DONE, sendMessage(Level.INFO, newStartedMessage($, port), $));
    }
}
