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

import bear.vcs.CommandLineResult;
import bear.cli.CommandLine;
import bear.cli.Script;
import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.java.JavaPlugin;
import bear.session.Result;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GrailsBuilder extends TaskDef {
    public static final Logger logger = LoggerFactory.getLogger(GrailsBuilder.class);

    GrailsPlugin grails;
    JavaPlugin java;
    Bear bear;

    public GrailsBuilder(GlobalContext global) {
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        bear = global.bear;
    }

    @Override
    public Task newSession(SessionContext $, final Task parent) {
        return new Task(parent, this, $) {
            @Override
            public GrailsBuildResult exec(TaskRunner runner) {
                $.log("building Grails WAR, rev: %s...", $(bear.realRevision));

                final String grailsExecPath = $(grails.grailsExecPath);

                String projectPath = $(grails.projectPath);

                final Script script = new Script($.sys)
                    .cd(projectPath);

                if ($(grails.clean)) {
                    script
                        .add(newGrailsCommand(grailsExecPath).a("clean"));
                }

                final String warName = $(grails.releaseWarPath);

                script.add(
                    newGrailsCommand(grailsExecPath).a(
                        "war",
                        warName));

                final CommandLineResult clResult = script.run();

                if(clResult.text.contains("Use --stacktrace to see the full trace")){
                    clResult.result = Result.ERROR;
                }

                return new GrailsBuildResult(clResult.result, $.joinPath(projectPath, warName));
            }

            private CommandLine newGrailsCommand(String grailsExecPath) {
                return $.sys.line()
                    .setVar("JAVA_HOME", $(java.homePath))
                    .a(grailsExecPath)
                    .timeoutMs(600000);
            }
        };
    }

}
