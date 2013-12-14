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

package bear.plugins.nodejs;

import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.console.ConsoleCallbackResultType;
import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.main.event.NoticeEventToUI;
import bear.plugins.misc.*;
import bear.plugins.play.ConfigureServiceInput;
import bear.plugins.play.ServerToolPlugin;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bear.core.SessionContext.ui;
import static bear.plugins.sh.CaptureInput.cap;
import static bear.plugins.sh.CopyOperationInput.cp;
import static bear.session.Variables.*;
import static java.lang.String.format;

/**
 * Starting Node.js apps on specific ports: http://stackoverflow.com/questions/18008620/node-js-express-js-app-only-works-on-port-3000.
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class NodeJsPlugin extends ServerToolPlugin {
    public static final Logger logger = LoggerFactory.getLogger(NodeJsPlugin.class);

    public static final ObjectMapper mapper = new ObjectMapper();

    public final DynamicVariable<String>
        appCommand,
        appEnv = newVar("production"),
        env = newVar("production"),
        user = equalTo(bear.sshUsername),
        consoleLogPath = concat(instanceLogsPath, "/", env, ".log"),
        execPath = equalTo(toolname)
            ;

    public final DynamicVariable<ObjectNode> packageJson = dynamic(new Fun<SessionContext, ObjectNode>() {
        @Override
        public ObjectNode apply(SessionContext $) {
            try {
                return (ObjectNode) mapper.readTree($.sys.readString($.var(releases.session).getCurrentRelease().get().path + "/package.json", null));
            } catch (IOException e) {
                throw Exceptions.runtime(e);
            }
        }
    }).memoizeIn(SessionContext.class);

    public NodeJsPlugin(GlobalContext global) {
        super(global);

        toolname.defaultTo("node", true);
        versionName.setEqualTo(concat(toolDistrName, "-v", version, "-linux-x64").temp());
        distrFilename.setEqualTo(concat(versionName, ".tar.gz").temp());
        distrWwwAddress.setEqualTo(Variables.format("http://nodejs.org/dist/v%s/%s", version, distrFilename));
        instancePorts.defaultTo("3000");

        appName.setEqualTo(dynamic(new Fun<AbstractContext, String>() {
            @Override
            public String apply(AbstractContext $) {
                return getString($.var(packageJson), "name", $.var(toolname) + "-app");
            }
        }).temp());

        appCommand = newVar("index.js");
    }

    public final DynamicVariable<Function<String, String>> simpleGruntUpstart = dynamic(new Fun<SessionContext, Function<String, String>>() {
        @Override
        public Function<String, String> apply(final SessionContext $) {
            return new Function<String, String>() {
                @Override
                public String apply(String port) {
                    String logPath = consoleLogPath(port, $);

                    resetConsolePath($, logPath);

                    return String.format("exec grunt >%s 2>&1", logPath);
                }
            };
        }
    });

    @Override
    public void initPlugin() {
        super.initPlugin();

        createScriptText.setDynamic(new Fun<SessionContext, Function<String, String>>() {
            @Override
            public Function<String, String> apply(final SessionContext $) {
                return new Function<String, String>() {
                    @Override
                    public String apply(String port) {
                        String releasePath = $.var(releases.activatedRelease).get().path;

                        final String logPath = consoleLogPath(port, $);

                        resetConsolePath($, logPath);

                        // from from http://ivarprudnikov.com/node-js-as-serivce-with-upstart-on-centos/
                        return "exec su -s /bin/sh -c 'exec \"$0\" \"$@\"' " + $.var(user) + " -- " + $.var(execPath) + ' ' + releasePath + '/' + $.var(appCommand) + " >" + logPath + " 2>&1";
                    }
                };
            }
        });
        
        configureService.setDynamic(new Fun<SessionContext, Function<ConfigureServiceInput, Void>>() {
            @Override
            public Function<ConfigureServiceInput, Void> apply(final SessionContext $) {
                return new Function<ConfigureServiceInput, Void>() {
                    @Override
                    public Void apply(ConfigureServiceInput input) {
                        input.service
//                            .setUser($.var(user))
                            .cd($.var(releases.activatedRelease).get().path)
                            .exportVar("NODE_ENV", $.var(env))
                            .exportVar("PORT", input.port + "");
                        
                        return null;
                    }
                };
            }
        });
    }

    public String consoleLogPath(String port, SessionContext $) {
        return format($.var(consoleLogPath), port);
    }


    //copied from play
    //todo extract common things
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
                public ConsoleCallbackResult progress(final AbstractConsole.Terminal console, String buffer, String wholeText) {
                    //todo these parts are all common, extract!!
                    if(buffer.contains("app crashed - waiting for file") ||   //nodemon message
                        buffer.contains("throw er; // Unhandled 'error' event")
                        ){
                        String message = "unable to start play instance on port " + port + ", release " + $.var(releases.session).getCurrentRelease().get().name();
                        ui.error(newNotice(message, $));

                        logger.error(message);

                        return new ConsoleCallbackResult(ConsoleCallbackResultType.EXCEPTION, message);
                    }

                    if( buffer.contains("Failed to load c++ bson extension") ||
                            buffer.contains("connect 3.0") ||
                            buffer.contains("starting `node")
                        ){
                       $.getGlobal().scheduler.schedule(new Runnable() {
                           @Override
                           public void run() {
                               String message = "seem to have started after timeout, node instance on port " + port + ", release " + $.var(releases.session).getCurrentRelease().get().name();

                               logger.info(message);
                               ui.info(newNotice(message, $));

                               console.finishWithResult(new ConsoleCallbackResult(ConsoleCallbackResultType.DONE, message));
                           }
                       }, 5, TimeUnit.SECONDS);
                    }

                    if (buffer.contains("Express app started on port")) {

                        String message = "started node instance on port " + port + ", release " + $.var(releases.session).getCurrentRelease().get().name();

                        ui.info(newNotice(message, $));

                        logger.info(message);

                        return new ConsoleCallbackResult(ConsoleCallbackResultType.DONE, message);
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

    private NoticeEventToUI newNotice(String message, SessionContext $) {
        return new NoticeEventToUI($.var(bear.fullName), message);
    }

    public final TaskDef<Task> build = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, def, $) {

                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("building the Node.js project ...");

                    PendingRelease pendingRelease = $.var(releases.pendingRelease);
                    Optional<Release> activeRelease = $.var(releases.activatedRelease);

                    $.sys.copy(cp($.var(projectPath) + "/*", pendingRelease.path));

                    if(activeRelease.isPresent()){
                        $.sys.copy(cp(activeRelease.get().path + "/node_modules", pendingRelease.path)).throwIfError();
                    }

                    return $.sys.captureResult(cap("npm install --loglevel warn").cd(pendingRelease.path));
                }
            };
        }
    });

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>(new SingleTaskSupplier() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new ZippedTool(parent, (InstallationTaskDef) def, $) {
                @Override
                protected DependencyResult exec(SessionTaskRunner runner, Object input) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut($(execName), "bin/" + $(execName));
                    shortCut("npm", "bin/npm");

                    $.sys.captureResult(cap("npm install -g grunt-cli").sudo()).throwIfError();

                    return verify();
                }

                @Override
                public Dependency asInstalledDependency() {
                    Dependency dep = super.asInstalledDependency();

                    dep.add(dep.new Command("grunt --version", ""));

                    return dep;
                }

                @Override
                protected String extractVersion(String output) {
                    return output.trim().substring(1);
                }

                @Override
                protected String createVersionCommandLine() {
                    return "node --version";
                }
            };

        }
    });

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }

    private static String getString(JsonNode node, String field, String _default) {
        JsonNode name = node.get(field);
        if(name != null) return name.asText();
        return _default;
    }
}
