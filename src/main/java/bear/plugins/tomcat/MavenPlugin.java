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

package bear.plugins.tomcat;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.VarFun;
import bear.plugins.ZippedToolPlugin;
import bear.task.*;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class MavenPlugin extends ZippedToolPlugin {
    public MavenPlugin(GlobalContext global) {
        super(global);

        version.defaultTo("3.0.5", true);
        toolname.defaultTo("maven", true);
        toolDistrName.defaultTo("apache-maven", true);
        distrFilename.setDynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return concat(versionName, "-bin.tar.gz");
            }
        });

        distrWwwAddress.setDynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return concat("http://apache-mirror.rbc.ru/pub/apache/maven/maven-3/", version,
                    "/binaries/apache-maven-", version, "-bin.tar.gz");
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

                    shortCut("mvn", "mvn");

                    return verify();
                }

                @Override
                protected String extractVersion(String output) {
                    return StringUtils.substringBetween(
                        output,
                        "Apache Maven ", " ");
                }

                @Override
                protected String createVersionCommandLine() {
                    return "mvn --version";
                }
            };
        }
    };


    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return install;
    }
}
