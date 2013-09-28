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

package cap4j.plugins.tomcat;

import cap4j.core.DependencyResult;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.core.VarFun;
import cap4j.plugins.ZippedToolPlugin;
import cap4j.session.DynamicVariable;
import cap4j.session.Variables;
import cap4j.task.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

import static cap4j.session.Variables.condition;
import static cap4j.session.Variables.joinPath;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TomcatPlugin extends ZippedToolPlugin {
    public final DynamicVariable<String>
    webappsUnix = joinPath(homePath, "webapps"),
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
        webapps = condition(cap.isUnix, webappsUnix, webappsWin);
        warPath = joinPath(webapps, warName);

        distrWwwAddress.setDynamic(new VarFun<String>() {
            public String apply() {
                return MessageFormat.format("http://apache-mirror.rbc.ru/pub/apache/tomcat/tomcat-7/v{0}/bin/apache-tomcat-{0}.tar.gz", $(version));
            }
        });
    }

    public void initPlugin() {
        global.tasks.restartApp.addBeforeTask(new TaskDef() {
            @Override
            public Task newSession(SessionContext $) {
                return new Task(this, $) {
                    @Override
                    protected TaskResult run(TaskRunner runner) {
                        $.sys.sudo().rm($.var(warCacheDirs));
                        $.sys.script()
                            .line().addRaw("tomcatStop").build()
                            .line().addRaw("tomcatStart").build()
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
                    }                };
            }
        });
    }

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>() {
        @Override
        public ZippedTool newSession(SessionContext $) {
            return new ZippedTool(this, $) {
                @Override
                protected DependencyResult run(TaskRunner runner) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut("tomcatStart", "startup.sh");
                    shortCut("tomcatStop", "shutdown.sh");
                    shortCut("tomcatVersion", "version.sh");

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
                    return "tomcatVersion";
                }
            };
        }
    };

    public final DynamicVariable<String[]> warCacheDirs = Variables.dynamic(new VarFun<String[]>() {
        public String[] apply() {
            final String name = FilenameUtils.getBaseName($.var(warName));
            return new String[]{
                $.sys.joinPath($.var(webapps), name)
            };
        }
    });


    @Override
    public InstallationTaskDef<ZippedTool> getInstall() {
        return install;
    }
}
