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

import cap4j.cli.Script;
import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.java.JavaPlugin;
import cap4j.cli.CommandLine;
import cap4j.scm.CommandLineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:25 PM
*/
public class GrailsBuilder {
    private static final Logger logger = LoggerFactory.getLogger(GrailsBuilder.class);

    SessionContext $;

    GrailsPlugin grails;
    JavaPlugin java;
    Cap cap;

    public GrailsBuilder(SessionContext $, GlobalContext global) {
        this.$ = $;
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        cap = global.cap;
    }

    public GrailsBuildResult build() {
        logger.info("building Grails WAR...");

        System.out.println($.var(cap.realRevision));

        final String grailsExecPath = $.var(grails.grailsExecPath);

        String projectPath = $.var(grails.projectPath);

        final Script script = new Script($.system)
            .cd(projectPath);

        if ($.varB(grails.clean)) {
            script
                .add(newGrailsCommand(grailsExecPath).a("clean"));
        }

        final String warName = $.var(grails.releaseWarPath);

        script.add(
            newGrailsCommand(grailsExecPath).a(
                "war",
                warName));

        final CommandLineResult clResult = $.system.run(script);

        return new GrailsBuildResult(clResult.result, $.joinPath(projectPath, warName));
    }

    private CommandLine newGrailsCommand(String grailsExecPath) {
        return $.newCommandLine()
            .setVar("JAVA_HOME", $.var(java.homePath))
            .a(grailsExecPath)
            .timeoutMs(600000);
    }
}
