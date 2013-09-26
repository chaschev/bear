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
import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.Plugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.scm.CommandLineResult;
import cap4j.session.DynamicVariable;
import cap4j.session.SystemEnvironment;
import cap4j.session.Variables;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

import static cap4j.session.Variables.*;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:28 PM
*/

/**
 * todo: change to zipped tool
 */
public class GrailsPlugin extends Plugin {
    public final DynamicVariable<String>
        homePath = newVar("/var/lib/grails").setDesc("Grails root dir"),
        homeParentPath = dynamic(new VarFun<String>() {
            public String apply() {
                return StringUtils.substringBeforeLast($.var(homePath), "/");
            }
        }),
        currentVersionPath = dynamic(new VarFun<String>() {
            public String apply() {
                return $.system.joinPath($.var(homeParentPath), "grails-" + $.var(version));
            }
        }),
        grailsBin = joinPath(homePath, "bin"),
        projectPath = dynamicNotSet("Project root dir"),
        grailsExecName = dynamic("grails or grails.bat", new VarFun<String>() {
            public String apply() {
                return "grails" + ($.system.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = condition(isSet(null, homePath),
            joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = newVar("ROOT.war").setDesc("i.e. ROOT.war"),
        projectWarPath = joinPath(projectPath, warName),
        releaseWarPath = condition(cap.isRemoteEnv, joinPath(cap.releasePath, warName), projectWarPath),
        version = dynamicNotSet(""),
        myDirPath,
        buildPath,
        distrFilename = dynamic(new VarFun<String>() {
            public String apply() {
                return "grails-" + $.var(version) + ".zip";
            }
        }),
        distrWwwAddress = dynamic(new VarFun<String>() {
            public String apply() {
                return MessageFormat.format("http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/%s", $.var(distrFilename));
            }
        })
    ;

    public final DynamicVariable<Boolean>
        clean = Variables.eql(cap.clean).setDesc("clean project before build")
    ;

    public final Task setup = new Task() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.rm($.var(buildPath));
            system.mkdirs($.var(buildPath));

            if(!system.exists(system.joinPath($.var(myDirPath), $.var(distrFilename)))){
                system.run(new Script(system)
                    .cd($.var(buildPath))
                    .add(system.line().timeoutMin(60).addRaw($.var(distrWwwAddress))));
            }

            final String homeParentPath = StringUtils.substringBeforeLast($.var(homePath), "/");

            final CommandLineResult r = system.run(new Script(system)
                .cd($.var(buildPath))
                .add(system.line().timeoutMin(1).addRaw("unzip ../%s", $.var(distrFilename)))
                .add(system.line().sudo().addRaw("rm -r %s", $.var(homePath)))
                .add(system.line().sudo().addRaw("mv %s %s", $.var(currentVersionPath), homeParentPath))
                .add(system.line().sudo().addRaw("ln -s %s %s", $.var(currentVersionPath), $.var(homePath)))
                .add(system.line().sudo().addRaw("chmod -R g+r,o+r %s", $.var(homePath)))
                .add(system.line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", $.var(homePath)))
                .add(system.line().sudo().addRaw("rm /usr/bin/grails"))
                .add(system.line().sudo().addRaw("ln -s %s/bin/grails /usr/bin/grails", $.var(currentVersionPath))),
                SystemEnvironment.passwordCallback($.var(cap.sshPassword))
            );

            System.out.println("verifying version...");
            final String installedVersion = StringUtils.substringAfter(
                system.run(system.line().timeoutSec(20).setVar("JAVA_HOME", $.var(global.getPlugin(JavaPlugin.class).homePath)).addRaw("grails --version")).text.trim(),
                "version: ");

            Preconditions.checkArgument($.var(version).equals(installedVersion),
                "versions don't match: %s (installed) vs %s (actual)", installedVersion, $.var(version));

            System.out.printf("successfully installed Grails %s%n", $.var(version));

            return new TaskResult(r);
        }
    };

    public GrailsPlugin(GlobalContext global) {
        super(global);

        myDirPath = Variables.joinPath(cap.sharedPath, "grails");
        buildPath = Variables.joinPath(myDirPath, "build");
    }

    @Override
    public Task getSetup() {
        return setup;
    }
}
