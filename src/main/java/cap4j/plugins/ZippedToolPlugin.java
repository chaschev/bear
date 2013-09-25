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

package cap4j.plugins;

import cap4j.cli.Script;
import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.java.JavaPlugin;
import cap4j.session.DynamicVariable;
import cap4j.task.Task;
import org.apache.commons.lang3.StringUtils;

import static cap4j.core.Cap.*;
import static cap4j.session.VariableUtils.concat;

/**
 * User: chaschev
 * Date: 8/30/13
 */

/**
 * A class that simplifies operations (i.e. installation) of tools like Maven, Grails, Play, Tomcat, etc
 */
public abstract class ZippedToolPlugin extends Plugin{
    public final DynamicVariable<String>
        version = dynamicNotSet("version of the tool"),
        toolname = dynamicNotSet("i.e. maven"),
        toolDistrName = strVar("i.e. apache-tomcat").setEqualTo(toolname),
        versionName = concat(toolDistrName, "-", version).setDesc("i.e. apache-maven-3.0.5"),
        distrFilename = concat(versionName, ".tar.gz"),
        homePath = concat("/var/lib/", toolname).setDesc("Tool root dir"),
        homeParentPath = dynamic(new VarFun<String>() {
            public String apply() {
                return StringUtils.substringBeforeLast($.var(homePath), "/");
            }
        }),
        homeVersionPath = concat(homeParentPath, "/", versionName).setDesc("i.e. /var/lib/apache-maven-7.0.42"),
        currentVersionPath = concat(homeParentPath, "/", versionName),

        myDirPath,
        buildPath,

        distrWwwAddress = dynamicNotSet("distribution download address");

    public ZippedToolPlugin(GlobalContext global) {
        super(global);
        myDirPath = concat(cap.sharedPath, "/", toolname).setDesc("a path in a shared dir, i.e. /var/lib/<app-name>/shared/maven");
        buildPath = concat(myDirPath, "/build");
    }

    protected abstract class ZippedToolTask extends Task {
        protected ZippedToolTask(String name) {
            super();
        }

        protected void clean(){
            system.rm($.var(buildPath));
            system.mkdirs($.var(buildPath));
        }

        protected void download(){
            if(!system.exists(system.joinPath($.var(myDirPath), $.var(distrFilename)))){
                system.script()
                    .cd($.var(myDirPath))
                    .line().timeoutMin(60).addRaw("wget %s", $.var(distrWwwAddress)).build();
            }
        }

        protected Script extractToHomeScript;

        protected abstract String extractVersion(String output);
        protected abstract String createVersionCommandLine();

        protected Script extractToHomeDir(){
            final String _distrFilename = $.var(distrFilename);

            extractToHomeScript = new Script(system)
                .cd($.var(buildPath));

            if(_distrFilename.endsWith("tar.gz")){
                extractToHomeScript.add(system.line().timeoutMin(1).addRaw("tar xvfz ../%s", _distrFilename));
            }else
            if(_distrFilename.endsWith("zip")){
                extractToHomeScript.add(system.line().timeoutMin(1).addRaw("unzip ../%s", $.var(distrFilename)));
            }

            extractToHomeScript
                .line().sudo().addRaw("rm -r %s", $.var(homePath)).build()
                .line().sudo().addRaw("rm -r %s", $.var(homeVersionPath)).build()
                .line().sudo().addRaw("mv %s %s", $.var(buildPath) + "/" + $.var(versionName), $.var(homeParentPath)).build()
                .line().sudo().addRaw("ln -s %s %s", $.var(currentVersionPath), $.var(homePath)).build()
                .line().sudo().addRaw("chmod -R g+r,o+r %s", $.var(homePath)).build()
                .line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", $.var(homePath)).build();

            return extractToHomeScript;
        }

        protected Script shortCut(String newCommandName, String sourceExecutableName){
            return extractToHomeScript.add(system.line().sudo().addRaw("rm /usr/bin/%s", newCommandName))
                .add(system.line().sudo().addRaw("ln -s %s/bin/%s /usr/bin/mvn", $.var(homePath), sourceExecutableName, newCommandName));
        }

        @Override
        public boolean verify(boolean throwException){
            $.log("verifying version for %s...", $.var(toolDistrName));

            final String versionText = system.run(system.line().setVar("JAVA_HOME", $.var(global.getPlugin(JavaPlugin.class).homePath)).addRaw(createVersionCommandLine())).text.trim();
            final String installedVersion = extractVersion(versionText);

            if($.var(version).equals(installedVersion)){
                $.log("version is ok for %s", $.var(versionName));

                return true;
            }else{
                if(throwException){
                    throw new RuntimeException(String.format("versions don't match: %s (installed) vs %s (expected)", installedVersion, $.var(version)));
                } else {
                    return false;
                }
            }
        }
    }
}
