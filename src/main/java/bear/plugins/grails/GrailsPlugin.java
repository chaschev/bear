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
import bear.plugins.ZippedToolPlugin;
import bear.plugins.java.JavaPlugin;
import bear.plugins.misc.ReleasesPlugin;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import org.apache.commons.lang3.StringUtils;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GrailsPlugin extends ZippedToolPlugin {
    ReleasesPlugin releases;

    public final DynamicVariable<String>
        grailsBin = joinPath(homePath, "bin"),
        projectPath = dynamic("Project root dir"),
        grailsExecName = concat("grails", condition(bear.isNativeUnix, newVar(""), newVar(".bat")).desc("'grails' or 'grails.bat'")),
        grailsExecPath = condition(isSet(homePath), joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = newVar("ROOT.war").desc("i.e. ROOT.war"),
        projectWarPath = joinPath(projectPath, warName),
        releaseWarPath;

    public final DynamicVariable<Boolean>
        clean = Variables.equalTo(bear.clean).desc("clean project before build")
    ;

    public GrailsPlugin(GlobalContext global) {
        super(global);

        toolname.defaultTo("grails", true);
        distrFilename.setEqualTo(concat(versionName, ".zip"));
        distrWwwAddress.setEqualTo(format("http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/%s", distrFilename));
        releaseWarPath = condition(bear.isRemoteEnv, joinPath(releases.releasePath, warName), projectWarPath);
    }


    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>() {
        @Override
        public ZippedTool newSession(SessionContext $, final Task parent) {
            return new ZippedTool(parent, this, $) {
                @Override
                protected DependencyResult exec(SessionTaskRunner runner, Object input) {
                    clean();

                    download();

                    extractToHomeDir();

                    shortCut("grails", "bin/grails");
                    shortCut("startGrails", "bin/startGrails");

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
