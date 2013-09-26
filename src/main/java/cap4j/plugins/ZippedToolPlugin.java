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
import cap4j.core.*;
import cap4j.plugins.java.JavaPlugin;
import cap4j.session.DynamicVariable;
import cap4j.session.Variables;
import cap4j.task.InstallationTask;
import cap4j.task.TaskResult;
import com.google.common.base.Predicate;
import org.apache.commons.lang3.StringUtils;

import static cap4j.session.Variables.concat;
import static cap4j.session.Variables.dynamic;

/**
 * User: chaschev
 * Date: 8/30/13
 */

/**
 * A class that simplifies operations (i.e. installation) of tools like Maven, Grails, Play, Tomcat, etc
 */
public abstract class ZippedToolPlugin extends Plugin{
    public final DynamicVariable<String>
        version = dynamic("version of the tool"),
        toolname = dynamic("i.e. maven"),
        toolDistrName = Variables.strVar("i.e. apache-tomcat").setEqualTo(toolname),
        versionName = concat(toolDistrName, "-", version).setDesc("i.e. apache-maven-3.0.5"),
        distrFilename = concat(versionName, ".tar.gz"),
        homePath = concat("/var/lib/", toolname).setDesc("Tool root dir"),
        homeParentPath = dynamic(new VarFun<String>() {
            public String apply() {
                return StringUtils.substringBeforeLast($(homePath), "/");
            }
        }),
        homeVersionPath = concat(homeParentPath, "/", versionName).setDesc("i.e. /var/lib/apache-maven-7.0.42"),
        currentVersionPath = concat(homeParentPath, "/", versionName),

        myDirPath,
        buildPath,

        distrWwwAddress = dynamic("distribution download address");

    public ZippedToolPlugin(GlobalContext global) {
        super(global);
        myDirPath = concat(cap.sharedPath, "/", toolname).setDesc("a path in a shared dir, i.e. /var/lib/<app-name>/shared/maven");
        buildPath = concat(myDirPath, "/build");
    }



    protected abstract class ZippedToolTask extends InstallationTask<TaskResult> {
        protected ZippedToolTask(String name) {
            super();
        }

        protected void clean(){
            system.rm($(buildPath));
            system.mkdirs($(buildPath));
        }

        protected void download(){
            if(!system.exists(system.joinPath($(myDirPath), $(distrFilename)))){
                system.script()
                    .cd($(myDirPath))
                    .line().timeoutMin(60).addRaw("wget %s", $(distrWwwAddress)).build()
                .run();
            }
        }

        protected Script extractToHomeScript;

        protected abstract String extractVersion(String output);
        protected abstract String createVersionCommandLine();

        protected Script extractToHomeDir(){
            final String _distrFilename = $(distrFilename);

            extractToHomeScript = new Script(system)
                .cd($(buildPath));

            if(_distrFilename.endsWith("tar.gz")){
                extractToHomeScript.add(system.line().timeoutMin(1).addRaw("tar xvfz ../%s", _distrFilename));
            }else
            if(_distrFilename.endsWith("zip")){
                extractToHomeScript.add(system.line().timeoutMin(1).addRaw("unzip ../%s", $(distrFilename)));
            }

            extractToHomeScript
                .line().sudo().addRaw("rm -r %s", $(homePath)).build()
                .line().sudo().addRaw("rm -r %s", $(homeVersionPath)).build()
                .line().sudo().addRaw("mv %s %s", $(buildPath) + "/" + $(versionName), $(homeParentPath)).build()
                .line().sudo().addRaw("ln -s %s %s", $(currentVersionPath), $(homePath)).build()
                .line().sudo().addRaw("chmod -R g+r,o+r %s", $(homePath)).build()
                .line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", $(homePath)).build();

            return extractToHomeScript;
        }

        protected Script shortCut(String newCommandName, String sourceExecutableName){
            return extractToHomeScript.add(system.line().sudo().addRaw("rm /usr/bin/%s", newCommandName))
                .add(system.line().sudo().addRaw("ln -s %s/bin/%s /usr/bin/mvn", $(homePath), sourceExecutableName, newCommandName));
        }


        @Override
        public DependencyResult checkDeps(boolean throwException){
//            $.log("verifying version for %s...", $(toolDistrName));
//
//            final Dependency dep = installedDependency();
//
//            final DependencyResult r = dep.checkDeps();
//
//            if(r.result.ok()){
//                $.log("version is ok for %s", $(versionName));
//            }
//
//            if(r.result.nok() && throwException){
//                throw new DependencyException(r.toString());
//            }
              return DependencyResult.OK;
        }

        @Override
        public Dependency installedDependency() {
            final Dependency dep = new Dependency($(toolDistrName), $);

            dep.add(dep.new Command(
                system.line().setVar("JAVA_HOME", $(global.getPlugin(JavaPlugin.class).homePath)).addRaw(createVersionCommandLine()),
                new Predicate<CharSequence>() {
                    @Override
                    public boolean apply(CharSequence s) {
                        return $(version).equals(extractVersion(s.toString()));
                    }
                },
                String.format("'%s' expected version: %s", $(toolDistrName), $(version))
            ));
            return dep;
        }
    }
}
