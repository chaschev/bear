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

package bear.plugins.java;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.ZippedToolPlugin;
import bear.session.DynamicVariable;
import bear.task.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.File;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.concat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class JavaPlugin extends ZippedToolPlugin {
    public DynamicVariable<String>
        localDistrPath;

    public final InstallationTaskDef<ZippedTool> install = new ZippedToolTaskDef<ZippedTool>(new TaskDef.SingleTaskSupplier() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new ZippedTool(parent, (InstallationTaskDef) def, $) {
                @Override
                protected DependencyResult exec(SessionTaskRunner runner, Object input) {
                    clean();

                    final File localDFile = new File(global.localCtx.var(localDistrPath));

                    if (!localDFile.exists()) {
                        return new DependencyResult($(toolname))
                            .add("expecting java distribution at " + localDFile.getAbsolutePath() + ". you may download it from Oracle.com site");
                    }

                    $.sys.upload($(myDirPath), localDFile);

                    extractToHomeDir();

                    shortCut("java", "bin/java");
                    shortCut("javah", "bin/javah");
                    shortCut("javac", "bin/javac");

                    return verify();
                }

                @Override
                protected String extractVersion(String output) {
                    return StringUtils.substringBetween(
                        output,
                        "java version \"", "\"");
                }

                @Override
                protected String createVersionCommandLine() {
                    return "java -version";
                }
            };
        }
    });



    public JavaPlugin(GlobalContext global) {
        super(global);

        LoggerFactory.getLogger("log").warn("JavaPlugin initialized");

        toolname.defaultTo("jdk");
        version.desc("version return by java, i.e. 1.7.0_40");

        versionName.desc("distribution file name with extension, i.e jdk-7u40-linux-x64");
        distrFilename.setEqualTo(concat(versionName, ".gz"));

        localDistrPath = joinPath(myDirPath, distrFilename);
    }


    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }
}
