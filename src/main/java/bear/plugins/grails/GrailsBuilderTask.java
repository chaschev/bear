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

package bear.plugins.grails;

import bear.core.SessionContext;
import bear.task.SessionTaskRunner;
import bear.task.SingleTaskSupplier;
import bear.vcs.CommandLineResult;
import bear.plugins.sh.CommandLine;
import bear.plugins.sh.Script;
import bear.core.Bear;
import bear.core.GlobalContext;
import bear.plugins.java.JavaPlugin;
import bear.session.Result;
import bear.task.Task;
import bear.task.TaskDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GrailsBuilderTask extends TaskDef {
    public static final Logger logger = LoggerFactory.getLogger(GrailsBuilderTask.class);

    GrailsPlugin grails;
    JavaPlugin java;
    Bear bear;

    //todo
    public GrailsBuilderTask(GlobalContext global) {
        super(new SingleTaskSupplier() {
            @Override
            public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
                final GrailsBuilderTask grailsTask = (GrailsBuilderTask) def;

                return new Task<TaskDef>(parent, def, $) {
                    @Override
                    public GrailsBuildResult exec(SessionTaskRunner runner, Object input) {
                        $.log("building Grails WAR, rev: %s...", $($.bear.realRevision));

                        final String grailsExecPath = $(grailsTask.grails.grailsExecPath);

                        String projectPath = $(grailsTask.grails.projectPath);

                        final Script script = new Script($.sys)
                            .cd(projectPath);

                        if ($(grailsTask.grails.clean)) {
                            script
                                .add(newGrailsCommand(grailsExecPath).a("clean"));
                        }

                        final String warName = $(grailsTask.grails.releaseWarPath);

                        script.add(
                            newGrailsCommand(grailsExecPath).a(
                                "war",
                                warName));

                        final CommandLineResult clResult = script.run();

                        if(clResult.text.contains("Use --stacktrace to see the full trace")){
                            clResult.setResult(Result.ERROR);
                        }

                        return new GrailsBuildResult(clResult.getResult(), $.joinPath(projectPath, warName));
                    }

                    private CommandLine newGrailsCommand(String grailsExecPath) {
                        return $.sys.line()
                            .setVar("JAVA_HOME", $(grailsTask.java.homePath))
                            .a(grailsExecPath)
                            .timeoutMs(600000);
                    }
                };
            }
        });
        grails = global.plugin(GrailsPlugin.class);
        java = global.plugin(JavaPlugin.class);
        bear = global.bear;
    }
}
