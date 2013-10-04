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

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.VarFun;
import bear.session.Variables;
import bear.task.DependencyResult;
import bear.task.InstallationTask;
import bear.plugins.ZippedToolPlugin;
import bear.plugins.java.JavaPlugin;
import bear.session.DynamicVariable;
import bear.task.InstallationTaskDef;
import bear.task.TaskRunner;
import org.apache.commons.lang3.StringUtils;

import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GrailsPlugin extends ZippedToolPlugin {
    public final DynamicVariable<String>
        homeParentPath = dynamic(new VarFun<String>() {
        public String apply() {
            return StringUtils.substringBeforeLast($(homePath), "/");
        }
    }),
        currentVersionPath = dynamic(new VarFun<String>() {
            public String apply() {
                return $.sys.joinPath($(homeParentPath), "grails-" + $(version));
            }
        }),
        grailsBin = joinPath(homePath, "bin"),
        projectPath = dynamic("Project root dir"),
        grailsExecName = dynamic("'grails' or 'grails.bat'", new VarFun<String>() {
            public String apply() {
                return "grails" + ($.sys.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = Variables.condition(Variables.isSet(null, homePath),
            joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = Variables.newVar("ROOT.war").setDesc("i.e. ROOT.war"),
        projectWarPath = joinPath(projectPath, warName),
        releaseWarPath = Variables.condition(bear.isRemoteEnv, joinPath(bear.releasePath, warName), projectWarPath)
    ;

    public final DynamicVariable<Boolean>
        clean = Variables.equalTo(bear.clean).setDesc("clean project before build")
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
                return String.format("http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/%s", $(distrFilename));
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

                    shortCut("grails", "grails");
                    shortCut("startGrails", "startGrails");

                    return verify();
                }

                @Override
                protected String extractVersion(String output) {
                    return StringUtils.substringAfter(
                        output,
                        "version: ").trim();
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
