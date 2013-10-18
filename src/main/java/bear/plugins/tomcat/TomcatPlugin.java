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

package bear.plugins.tomcat;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.VarFun;
import bear.plugins.AbstractContext;
import bear.plugins.ZippedToolPlugin;
import bear.session.BearVariables;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TomcatPlugin extends ZippedToolPlugin {
    public final DynamicVariable<String>
    webappsUnix = BearVariables.joinPath(homePath, "webapps"),
        webappsWin = Variables.dynamic(""),
        webapps,
        warName = Variables.strVar("i.e. ROOT.war"),
        warPath,

    tomcatBasePort = Variables.newVar("8005"),
        tomcatAjpPort = Variables.newVar("8009"),
        tomcatHttpPort = Variables.newVar("8080"),
        tomcatHttpsPort = Variables.newVar("8443"),
        keystrokePassword = Variables.dynamic(""),
        catalinaHome = Variables.newVar("/usr/share/tomcat6"),
        catalinaExecutable = Variables.newVar("/usr/sbin/tomcat6");


    public TomcatPlugin(GlobalContext global) {
        super(global);

        version.defaultTo("7.0.42", true);
        toolname.defaultTo("tomcat", true);
        toolDistrName.defaultTo("apache-tomcat", true);

//        webapps = condition(cap.isUnix, webappsUnix, webappsWin);
        webapps = Variables.condition(bear.isUnix, webappsUnix, webappsWin);
        warPath = BearVariables.joinPath(webapps, warName);

        distrWwwAddress.setDynamic(new VarFun<String, AbstractContext>() {
            public String apply() {
                String version = $(TomcatPlugin.this.version);

                switch (version.charAt(0)){
                    case '6':
                        return MessageFormat.format("http://apache-mirror.rbc.ru/pub/apache/tomcat/tomcat-6/v{0}/bin/apache-tomcat-{0}.tar.gz", version);
                    default:
                        return MessageFormat.format("http://apache-mirror.rbc.ru/pub/apache/tomcat/tomcat-7/v{0}/bin/apache-tomcat-{0}.tar.gz", version);

                }
            }
        });
    }

    public void initPlugin() {
        //screen recipe is taken from here http://stackoverflow.com/a/1628217/1851024
        global.tasks.restartApp.addBeforeTask(new TaskDef() {
            @Override
            public Task<TaskDef> newSession(SessionContext $, final Task parent) {
                return new Task<TaskDef>(parent, this, $) {
                    @Override
                    protected TaskResult exec(TaskRunner runner) {
                        $.sys.sudo().rm($(warCacheDirs));
                        $.sys.script()
                            .line().addRaw("catalina stop").build()
                            .line().addRaw("nohup catalina start").build()
                            .timeoutSec(60)
                            .run();
//                        $.sys.sudo().run($.newCommandLine()
//                            .a("service", "tomcat6", "stop")
//                            .semicolon()
//                            .sudo()
//                            .a("service", "tomcat6", "start")
//                            .timeoutMin(2)
//                        );

                        return TaskResult.OK;
                    }
                };
            }
        });
    }

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>() {
        @Override
        public ZippedTool newSession(SessionContext $, final Task parent) {
            return new ZippedTool(parent, this, $) {
                @Override
                protected DependencyResult exec(TaskRunner runner) {
                    clean();

                    download();

                    extractToHomeDir();

//                    shortCut("tomcatStart", "startup.sh");
//                    shortCut("tomcatStop", "shutdown.sh");
//                    shortCut("tomcatVersion", "version.sh");
                    shortCut("catalina", "catalina.sh");

                    DependencyResult result = verify();

                    $.sys.move($(webapps)+"/ROOT", $(webapps)+"/conf");

                    return result;
                }

                @Override
                protected String extractVersion(String output) {
                    return StringUtils.substringBetween(
                        output,
                        "Server version: Apache Tomcat/", "\r");
                }

                @Override
                protected String createVersionCommandLine() {
                    return "catalina version";
                }
            };
        }
    };

    public final DynamicVariable<String[]> warCacheDirs = Variables.dynamic(new VarFun<String[], SessionContext>() {
        public String[] apply() {
            final String name = FilenameUtils.getBaseName($(warName));
            return new String[]{
                $.sys.joinPath($(webapps), name)
            };
        }
    });


    @Override
    public InstallationTaskDef<ZippedTool> getInstall() {
        return install;
    }
}
