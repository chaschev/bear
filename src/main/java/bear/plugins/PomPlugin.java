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

package bear.plugins;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PomPlugin extends Plugin{
    Bear bear;

    public PomPlugin(GlobalContext global) {
        super(global);
    }

    public String generate(){
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "\n" +
            "    <groupId>bear</groupId>\n" +
            "    <artifactId>" + global.var(bear.applicationName) + "</artifactId>\n" +
            "    <version>1.0-SNAPSHOT</version>\n" +
            "\n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>com.chaschev</groupId>\n" +
            "            <artifactId>bear</artifactId>\n" +
            "            <version>1.0-SNAPSHOT</version>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "    <build>\n" +
            "        <sourceDirectory>${basedir}/.bear</sourceDirectory>\n" +
            "    </build>\n" +
            "</project>\n";
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }
}
