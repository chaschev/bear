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

import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.context.AbstractContext;
import bear.plugins.ZippedToolPlugin;
import bear.plugins.java.JavaPlugin;
import bear.session.BearVariables;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import org.apache.commons.lang3.StringUtils;

import static bear.session.Variables.dynamic;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GrailsPlugin extends ZippedToolPlugin {
    public final DynamicVariable<String>
        homeParentPath = dynamic(new Fun<String, AbstractContext>() {
        public String apply(AbstractContext $) {
            return StringUtils.substringBeforeLast($.var(homePath), "/");
        }
    }),
        currentVersionPath = dynamic(new Fun<String, SessionContext>() {
            public String apply(SessionContext $) {
                return $.sys.joinPath($.var(homeParentPath), "grails-" + $.var(version));
            }
        }),
        grailsBin = BearVariables.joinPath(homePath, "bin"),
        projectPath = dynamic("Project root dir"),
        grailsExecName = dynamic("'grails' or 'grails.bat'", new Fun<String, SessionContext>() {
            public String apply(SessionContext $) {
                return "grails" + ($.sys.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = Variables.condition(Variables.isSet(null, homePath),
            BearVariables.joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = Variables.newVar("ROOT.war").desc("i.e. ROOT.war"),
        projectWarPath = BearVariables.joinPath(projectPath, warName),
        releaseWarPath = Variables.condition(bear.isRemoteEnv, BearVariables.joinPath(bear.releasePath, warName), projectWarPath)
    ;

    public final DynamicVariable<Boolean>
        clean = Variables.equalTo(bear.clean).desc("clean project before build")
    ;

    public GrailsPlugin(GlobalContext global) {
        super(global);

        toolname.defaultTo("grails", true);
        distrFilename.setDynamic(new Fun<String, SessionContext>() {
            @Override
            public String apply(SessionContext $) {
                return $.concat(versionName, ".zip");
            }
        });
        distrWwwAddress.setDynamic(new Fun<String, SessionContext>() {
            public String apply(SessionContext $) {
                return String.format("http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/%s", $.var(distrFilename));
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
