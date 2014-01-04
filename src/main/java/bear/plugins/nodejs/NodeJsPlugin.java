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
import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.ConfigureServiceInput;
import bear.plugins.ServerToolPlugin;
import bear.plugins.misc.*;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.base.Optional;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bear.session.Variables.*;

/**
 * Starting Node.js apps on specific ports: http://stackoverflow.com/questions/18008620/node-js-express-js-app-only-works-on-port-3000.
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class NodeJsPlugin extends ServerToolPlugin {

    public static final ObjectMapper mapper = new ObjectMapper();

    public final DynamicVariable<String>
        appCommand,
        appEnv = newVar("production")

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
                            .setUser($.var(user))
                            .cd($.var(releases.activatedRelease).get().path)
                            .exportVar("NODE_ENV", $.var(env).name())
                            .exportVar("PORT", input.port + "");
                        
                        return null;
                    }
                };
            }
        });
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
                        buffer.contains("throw er; // Unhandled 'error' event")){

                        return notStartedResult($, port);
                    }

                    //I found no message indicating that Node started
                    if( buffer.contains("Failed to load c++ bson extension") ||
                            buffer.contains("connect 3.0") ||
                            buffer.contains("starting `node")
                        ){
                        seemsHaveStarted(console, $, port);
                    }

                    if (buffer.contains("Express app started on port")) {
                        return startedResult($, port);
                    }

                    return ConsoleCallbackResult.CONTINUE;
                }
            })
                .setTimeoutMs($.var(startupTimeoutMs))
            );

            watchDogGroup.add(runnable);
        }

        watchDogGroup.startThreads();

        watchDogGroup.scheduleForcedShutdown($.getGlobal().getScheduler(), $.var(bear.appStartTimeoutSec), TimeUnit.SECONDS);
    }

    public final TaskDef<Object, TaskResult<?>> build = new TaskDef<Object, TaskResult<?>>(new SingleTaskSupplier<Object, TaskResult<?>>() {
        @Override
        public Task<Object, TaskResult<?>> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<Object, TaskResult<?>> def) {
            return new Task<Object, TaskResult<?>>(parent, def, $) {

                @Override
                protected TaskResult<?> exec(SessionRunner runner) {
                    $.log("building the Node.js project ...");

                    PendingRelease pendingRelease = $.var(releases.pendingRelease);
                    Optional<Release> activeRelease = $.var(releases.activatedRelease);

                    $.sys.copy($.var(projectPath) + "/*").to(pendingRelease.path).run();

                    //this will be the previous active release
                    if(activeRelease.isPresent()){
                        $.sys.copy(activeRelease.get().path + "/node_modules").to(pendingRelease.path).run().throwIfError();
                    }

                    return $.sys.captureBuilder("npm install --loglevel warn").inDir(pendingRelease.path).run();
                }
            };
        }
    });

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>(new SingleTaskSupplier<Object, TaskResult<?>>() {
        @Override
        public Task<Object, TaskResult<?>> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<Object, TaskResult<?>> def) {
            return new ZippedTool(parent, (InstallationTaskDef) def, $) {
                @Override
                protected TaskResult<?> exec(SessionRunner runner) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut($(execName), "bin/" + $(execName));
                    shortCut("npm", "bin/npm");

                    $.sys.captureBuilder("npm install -g grunt-cli").sudo().run().throwIfError();

                    // a fix for Ubuntu: ~/tmp is being created after installing grunt as sudo
                    if($.sys.exists("tmp")){
                        $.sys.rm("tmp").force().sudo().run().throwIfError();
                    }

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
