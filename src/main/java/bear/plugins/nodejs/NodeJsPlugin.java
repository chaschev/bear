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

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.misc.PendingRelease;
import bear.plugins.misc.Release;
import bear.plugins.misc.UpstartService;
import bear.plugins.play.ServerToolPlugin;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

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
        stderrLogPath = concat(instanceLogsPath, "/", env, ".err.log"),
        stdoutLogPath = concat(instanceLogsPath,"/", env, ".out.log"),
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

        appName.setEqualTo(dynamic(new Fun<AbstractContext, String>() {
            @Override
            public String apply(AbstractContext $) {
                return getString($.var(packageJson), "name", $.var(toolname) + "-app");
            }
        }).temp());

        appCommand = newVar("index.js");
    }


    @Override
    public void initPlugin() {
        super.initPlugin();
        instancePath.setEqualTo(concat(releases.currentReleaseLinkPath, "/instances/play-%s"));
    }

    @Override
    protected void configureService(UpstartService upstartService, SessionContext $, String port) {
        upstartService
            .setUser($.var(user))
            .exportVar("NODE_ENV", $.var(env))
            .exportVar("PORT", port + "");
    }

    @Override
    protected String createScriptText(SessionContext $, String port) {
        String path = $.var(releases.activatedRelease).get().path;

        return "exec " + $.var(execPath) + " " + path + "/" + $.var(appCommand) + " 2>> " +
            format($.var(stderrLogPath), port) +
            " 1>> " +
            format($.var(stdoutLogPath), port);
    }

    @Override
    protected void spawnStartWatchDogs(SessionContext $, List<String> ports) {
/*
        final WatchDogGroup watchDogGroup = new WatchDogGroup(ports.size(), watchStartDogGroup);

        for (final String port : ports) {
            String consolePath = lo($, port);

            // to make sure there are no old start entries
            resetConsolePath($, consolePath);

            WatchDogRunnable runnable = new WatchDogRunnable($, watchDog, new WatchDogInput(
                consolePath, false, new ConsoleCallback() {
                @Override
                @Nonnull
                public ConsoleCallbackResult progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                    if (buffer.contains("Listening for HTTP on")) {
//                                    logger.debug("OOOOOOOOOOOOPS - listening!");

                        SessionContext.ui.info(new NoticeEventToUI($.var(bear.fullName),
                            "started play instance on port " + port + ", release " + $.var(releases.session).getCurrentRelease().get().name()));

                        return new ConsoleCallbackResult(ConsoleCallbackResultType.DONE, null);
                    }

                    if(buffer.contains("Oops, cannot start the server.")){
//                                    logger.debug("OOOOOOOOOOOOPS - found");
                        String message = "unable to start play instance on port " + port + ", release " + $.var(releases.session).getCurrentRelease().get().name();
                        SessionContext.ui.error(new NoticeEventToUI($.var(bear.fullName),
                            message));

                        return new ConsoleCallbackResult(ConsoleCallbackResultType.EXCEPTION, message);
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
*/

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
