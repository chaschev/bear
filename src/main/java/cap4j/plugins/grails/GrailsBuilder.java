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

package cap4j.plugins.grails;

import cap4j.cli.CommandLine;
import cap4j.cli.Script;
import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.java.JavaPlugin;
import cap4j.vcs.CommandLineResult;
import cap4j.session.Result;
import cap4j.task.Task;
import cap4j.task.TaskDef;
import cap4j.task.TaskRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GrailsBuilder extends TaskDef {
    public static final Logger logger = LoggerFactory.getLogger(GrailsBuilder.class);

    GrailsPlugin grails;
    JavaPlugin java;
    Cap cap;

    public GrailsBuilder(GlobalContext global) {
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        cap = global.cap;
    }

    @Override
    public Task newSession(SessionContext $) {
        return new Task(this, $) {
            @Override
            public GrailsBuildResult run(TaskRunner runner) {
                $.log("building Grails WAR, rev: %s...", $(cap.realRevision));

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
                    .setVar("JAVA_HOME", $.var(java.homePath))
                    .a(grailsExecPath)
                    .timeoutMs(600000);
            }
        };
    }

}
