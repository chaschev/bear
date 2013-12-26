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
import com.google.common.base.Function;
import org.apache.commons.io.FilenameUtils;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.*;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.substringBetween;

/**
 * https://gist.github.com/alanfranz/6902429 - tomcat6 upstart script
 * http://stackoverflow.com/questions/16110528/tomcat-multiple-instances-simultaneously?answertab=active#tab-top
 * http://kief.com/running-multiple-tomcat-instances-on-one-server.html
 *
 * $CATALINA_HOME represents the root of the Tomcat installation; in CentOS the default value is /usr/share/tomcat6. Under the root you'll find two directories:
 * $CATALINA_BASE is used when Tomcat is configured to run multiple instances. If defined, Tomcat calculates all relative references for files in the following directories on the basis of the value set for CATALINA_BASE instead of CATALINA_HOME:
 *
 * https://github.com/rhunter/capistrano-tomcat/blob/master/lib/capistrano/tomcat.rb
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TomcatPlugin extends ServerToolPlugin {
    UpstartPlugin upstart;
    ReleasesPlugin releases;

    public final DynamicVariable<String>
        webappsUnix = joinPath(homePath, "webapps"),
        webappsWin = dynamic(""),
        webappsPath = concat(instancePath, "/webapps"),
        confPath = concat(instancePath, "/conf"),
        logsPath = concat(instancePath, "/logs"),
        tempPath = concat(instancePath, "/temp"),
        workPath = concat(instancePath, "/work"),
        warName = strVar("i.e. ROOT.war"),
        instanceWarPath = joinPath(instancePath, warName),
        javaOpts = newVar("-Djava.awt.headless=true"),
        catalinaOpts = newVar("\"-Xms512M -Xmx1024M -server -XX:+UseParallelGC -XX:PermSize=128m -XX:MaxPermSize=256m\""),
        basePort = newVar("8005"),
        ajpPort = newVar("8009"),
        httpPort = newVar("8080"),
        httpsPort = newVar("8443"),
        keystrokePassword = Variables.dynamic(""),
        catalinaHome = equalTo(homePath);


    public TomcatPlugin(GlobalContext global) {
        super(global);

        version.set("6.0.37");
        toolname.set("tomcat");
        toolDistrName.set("apache-tomcat");

        instancePorts.set("8080");
        consoleLogPath.setEqualTo(concat(logsPath, "/", envString, ".log").temp());
//        instanceLogsPath.setEqualTo(concat(logsPath));
//        webapps = condition(cap.isUnix, webappsUnix, webappsWin);

        execPath.setEqualTo(concat(catalinaHome, "/bin/catalina.sh"));

        multiServiceName.setEqualTo(concat(toolname, "_%s"));
        singleServiceName.setEqualTo(toolname);

        distrWwwAddress.setDynamic(new Fun<AbstractContext, String>() {
            public String apply(AbstractContext $) {
                String version = $.var(TomcatPlugin.this.version);

                switch (version.charAt(0)){
                    case '6':
                        return MessageFormat.format("http://apache-mirror.rbc.ru/pub/apache/tomcat/tomcat-6/v{0}/bin/apache-tomcat-{0}.tar.gz", version);
                    default:
                        return MessageFormat.format("http://apache-mirror.rbc.ru/pub/apache/tomcat/tomcat-7/v{0}/bin/apache-tomcat-{0}.tar.gz", version);

                }
            }
        });
    }

    @Override
    public void initPlugin() {
        super.initPlugin();

        instancePath.setEqualTo(concat(homePath, "/instances/", toolname, "-%s"));
    }


    // copied from NodePlugin!!
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
                    //todo add real crash messages
                    if(buffer.contains("app crashed - waiting for file") ){
                        return notStartedResult($, port);
                    }

                    if (buffer.contains("Server startup in")) {
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

        watchDogGroup.scheduleForcedShutdown($.getGlobal().scheduler, $.var(bear.appStartTimeoutSec), TimeUnit.SECONDS);
    }

    public final TaskDef<Object, TaskResult> deployWar = new TaskDef<Object, TaskResult>(new TaskCallable<Object, TaskResult>() {
        @Override
        public TaskResult call(SessionContext $, Task<Object, TaskResult> task) throws Exception {
            String warSourcePath = $.var(releases.activatedRelease).get().path + "/" + $.var(warName);

            for (String port : $.var(portsSplit)) {
                String tomcatPath = path(webappsPath, port, $) + "/" + FilenameUtils.getBaseName($.var(warName));

                $.sys.rm(tomcatPath).sudo().run();
                $.sys.mkdirs(tomcatPath).withUser($.var(user)).run();

                $.sys.unzip(warSourcePath).to(tomcatPath).withUser($.var(user)).run().throwIfError();
            }

            return TaskResult.OK;
        }
    });

    // todo add                         $.sys.rm(RmInput.newRm($(warCacheDirs)).sudo());

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>(new SingleTaskSupplier<Object, TaskResult>() {
        @Override
        public Task<Object, TaskResult> createNewSession(SessionContext $, final Task<Object, TaskResult> parent, final TaskDef<Object, TaskResult> def) {
            return new ZippedTool(parent, (InstallationTaskDef) def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut("catalina", "bin/catalina.sh");

                    DependencyResult result = verify();

                    String serverXmlAsString = $.sys.readString($(homePath) + "/conf/server.xml", null);

                    List<String> ports = $.var(portsSplit);

                    int firstPortI = parseInt(ports.get(0));

                    logger.info("creating folder structure for {} instances", ports.size());

                    String userWithGroup = $(TomcatPlugin.this.userWithGroup);

                    for (final String port : ports) {
                        final String instancePath = instancePath(port, $);

                        String[] dirs = {$(webappsPath), $(tempPath), $(logsPath), $(confPath), $(workPath)};

                        for (int i = 0; i < dirs.length; i++) {
                            dirs[i] = format(dirs[i], port);
                        }

                        $.sys.mkdirs(dirs).sudo().withUser(userWithGroup).run();
                        $.sys.copy($(homePath) + "/conf").to(instancePath).sudo().withUser(userWithGroup).run();

                        int portI = parseInt(port);

                        int diff = portI - firstPortI;

                        //todo make links for logs in /var/log

                        String instanceServerXml = serverXmlAsString
                            .replace("port=\"8080\"", "port=\"" + (firstPortI + diff) + "\"")
                            .replace("port=\"8005\"", "port=\"" + (parseInt($(basePort)) + diff) + "\"")
                            .replace("redirectPort=\"8443\"", "redirectPort=\"" + (parseInt($(httpsPort)) + diff) + "\"")
                            .replace("port=\"8009\"", "port=\"" + (parseInt($(ajpPort)) + diff) + "\"")
                            ;

                        $.sys.writeString(instanceServerXml)
                            .toPath(format($(confPath) + "/server.xml", port))
                            .sudo()
                            .withUser(userWithGroup)
                            .run();

                        $.put(configureService, newBasicUpstartConfigurator($));

                        $.put(createScriptText, newBasicUpstartScriptText($));
                    }

                    TaskResult upstartResult = $.runSession(
                        upstart.create.singleTaskSupplier().createNewSession($, parent, upstart.create),
                        $.var(customUpstart)
                    );

                    if(upstartResult.nok()){
                        result.add(upstartResult.toString());
                    }

                    return result;
                }

                @Override
                protected String extractVersion(String output) {
                    return substringBetween(output, "Server version: Apache Tomcat/", "\r");
                }

                @Override
                protected String createVersionCommandLine() {
                    return "catalina version";
                }
            };
        }
    });

    public Function<String, String> newBasicUpstartScriptText(final SessionContext $) {
        return new Function<String, String>() {
            @Override
            public String apply(String port) {
                $.sys.rm(path(instanceLogsPath, port, $)).run();
                $.sys.link(path(instanceLogsPath, port, $)).toSource(path(logsPath, port, $));

                final String logPath = consoleLogPath(port, $);

                resetConsolePath($, logPath);

                return "exec su -s /bin/sh -c 'exec \"$0\" \"$@\"' " + $.var(TomcatPlugin.this.user) + " -- " +
                    $.var(execPath) + " run " + logPath + " >" + logPath + " 2>&1";
            }
        };
    }

    public Function<ConfigureServiceInput, Void> newBasicUpstartConfigurator(final SessionContext $) {
        return new Function<ConfigureServiceInput, Void>() {
            @Override
            public Void apply(ConfigureServiceInput in) {
                String tempPath = path(TomcatPlugin.this.tempPath, in.port, $);

                in.service
                    .exportVar("CATALINA_BASE", instancePath(in.port, $))
                    .exportVar("CATALINA_HOME", $.var(homePath))
                    .exportVar("CATALINA_TMPDIR", tempPath)
                ;

                if($.isSet(javaOpts) || javaOpts.isDefined()){
                    in.service.exportVar("JAVA_OPTS", $.var(javaOpts));
                }

                if($.isSet(catalinaOpts) || catalinaOpts.isDefined()){
                    in.service.exportVar("CATALINA_OPTS", $.var(catalinaOpts));
                }

                in.service.setCustom("\n" +
                    "# cleanup temp directory after stop\n" +
                    "post-stop script \n" +
                    "  rm -rf " + tempPath +"/*\n" +
                    "end script");

                return null;
            }
        };
    }

    @Override
    public InstallationTaskDef<ZippedTool> getInstall() {
        return install;
    }
}
