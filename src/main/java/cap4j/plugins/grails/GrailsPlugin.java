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

import cap4j.core.*;
import cap4j.plugins.ZippedToolPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.session.DynamicVariable;
import cap4j.session.Variables;
import cap4j.task.InstallationTask;
import cap4j.task.InstallationTaskDef;
import cap4j.task.TaskRunner;
import org.apache.commons.lang3.StringUtils;

import static cap4j.session.Variables.*;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:28 PM
*/

/**
 * todo: change to zipped tool
 */
public class GrailsPlugin extends ZippedToolPlugin {
    public final DynamicVariable<String>
        homeParentPath = dynamic(new VarFun<String>() {
        public String apply() {
            return StringUtils.substringBeforeLast($.var(homePath), "/");
        }
    }),
        currentVersionPath = dynamic(new VarFun<String>() {
            public String apply() {
                return $.sys.joinPath($.var(homeParentPath), "grails-" + $.var(version));
            }
        }),
        grailsBin = joinPath(homePath, "bin"),
        projectPath = dynamic("Project root dir"),
        grailsExecName = dynamic("grails or grails.bat", new VarFun<String>() {
            public String apply() {
                return "grails" + ($.sys.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = condition(isSet(null, homePath),
            joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = newVar("ROOT.war").setDesc("i.e. ROOT.war"),
        projectWarPath = joinPath(projectPath, warName),
        releaseWarPath = condition(cap.isRemoteEnv, joinPath(cap.releasePath, warName), projectWarPath)
    ;

    public final DynamicVariable<Boolean>
        clean = Variables.eql(cap.clean).setDesc("clean project before build")
    ;

    public GrailsPlugin(GlobalContext global) {
        super(global);

        toolname.defaultTo("grails", true);
        distrFilename.setDynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return concat(versionName, ".zip");
            }
        });
        distrWwwAddress.setDynamic(new VarFun<String>() {
            public String apply() {
                return String.format("http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/%s", $.var(distrFilename));
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

                    buildExtractionToHomeDir();

                    shortCut("grails", "grails");
                    shortCut("startGrails", "startGrails");

                    return extractAndVerify();
                }

                @Override
                protected String extractVersion(String output) {
                    return StringUtils.substringAfter(
                        output,
                        "version: ");
                }

                @Override
                protected String createVersionCommandLine() {
                    return "grails --version";
                }
            };
        }
    };

    @Override
    public DependencyResult checkPluginDependencies() {
        return require(JavaPlugin.class);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }
}
