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

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.java.JavaPlugin;
import bear.plugins.sh.CommandLine;
import bear.plugins.sh.Script;
import bear.session.DynamicVariable;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import static bear.plugins.sh.RmInput.newRm;
import static bear.session.Variables.*;
import static chaschev.lang.Predicates2.contains;
import static com.google.common.base.Predicates.or;
import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Iterables.find;

/**
 * User: chaschev
 * Date: 8/30/13
 */

/**
 * A class that simplifies operations (i.e. installation) of tools like Maven, Grails, Play, Tomcat, etc
 */
public class ZippedToolPlugin extends Plugin<Task, TaskDef<?>> {
    public final DynamicVariable<String>
        version = dynamic("version of the tool, a string which is return by a tool identifying it's version"),
        toolname = dynamic("this will be the name of home folder, i.e. maven, jdk"),
        toolDistrName = strVar("i.e. apache-tomcat").setEqualTo(toolname),
        versionName = concat(toolDistrName, "-", version).desc("i.e. apache-maven-3.0.5"),
        distrFilename = concat(versionName, ".tar.gz"),
        homeParentPath = concat(bear.toolsInstallDirPath, "/", toolname),
        homePath = concat(homeParentPath, "/", version).desc("Tool root dir"),
//        homeVersionPath = equalTo(homePath),
        currentVersionPath = concat(homeParentPath, "/current"),

        myDirPath,
        buildPath,

        distrWwwAddress = dynamic("distribution download address");

    public ZippedToolPlugin(GlobalContext global) {
        super(global);

        myDirPath = concat(bear.toolsSharedDirPath, "/", toolname).desc("a path in a shared dir, i.e. /var/lib/<app-name>/shared/maven");
        buildPath = concat(myDirPath, "/build");
    }

    protected class ZippedToolTaskDef<T extends ZippedTool> extends InstallationTaskDef<T>{

        public ZippedToolTaskDef(SingleTaskSupplier singleTaskSupplier) {
            super(singleTaskSupplier);
        }
    }

    protected abstract class ZippedTool extends InstallationTask<InstallationTaskDef> {
        protected ZippedTool(Task<TaskDef> parent, InstallationTaskDef def, SessionContext $) {
            super(parent, def, $);

            addDependency(new Dependency(toString(), $).addCommands(
                "unzip -v | head -n 1",
                " wget --version | head -n 1"));
        }

        @Override
        protected DependencyResult exec(SessionRunner runner, Object input) {
            throw new UnsupportedOperationException("todo implement!");
        }

        protected Script extractToHomeScript;

        protected abstract String extractVersion(String output);
        protected abstract String createVersionCommandLine();

        @Override
        public Dependency asInstalledDependency() {
            final Dependency dep = new Dependency($(toolDistrName) + " installation", $);

            CommandLine line = $.sys.line();
            Optional<JavaPlugin> java = global.getPlugin(JavaPlugin.class);

            if(java.isPresent()){
                line.setVar("JAVA_HOME", $(java.get().homePath));
            }

            line.addRaw(createVersionCommandLine());

            dep.add(dep.new Command(
                line,
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

        protected void clean(){
            $.sys.rm(newRm($(buildPath)));
            $.sys.mkdirs($(buildPath));
        }

        protected void download(){
            if(!$.sys.exists($.sys.joinPath($(myDirPath), $(distrFilename)))){
                String url = $(distrWwwAddress);
                CommandLineResult run = $.sys.script()
                    .cd($(myDirPath))
                    .line().timeoutMin(60).addRaw("wget %s", url).build()
                    .run();

                Predicate<String> errorPredicate = or(contains("404 Not Found"), contains("ERROR"));

                if(errorPredicate.apply(run.text)){
                    throw new RuntimeException("Error during download of " + url +
                        ": " + find(on('\n').split(run.text), errorPredicate));
                }
            }
        }


        protected Script extractToHomeDir(){
            final String distrName = $(distrFilename);

            Script script = $.sys.script()
                .cd($(buildPath))
                .timeoutSec(60);

            if(distrName.endsWith("tar.gz")){
                script.line().addRaw("tar xvfz ../%s", distrName).build();
            }else
            if(distrName.endsWith("gz")){
                script.line().addRaw("tar xvf ../%s", distrName).build();
            }else
            if(distrName.endsWith("zip")){
                script.line().addRaw("unzip ../%s", distrName).build();
            }else
            if(distrName.endsWith("bin")){
                script
                    .line().addRaw("chmod u+x %s", distrName).build()
                    .line().addRaw("./%s", distrName).build();
            }else{
                throw new IllegalArgumentException("unsupported archive type: " + distrName);
            }

            script
                .timeoutSec(60)
                .run();

            String toolDirName = $.sys.capture(String.format("cd %s && ls -w 1", $(buildPath))).trim();

            if(!toolDirName.equals($(versionName))){
                $.warn("tool version name is not equal to tool dir name: (expected) %s vs (actual) %s. setting new tool name to %s", $(versionName), toolDirName, toolDirName);
                versionName.defaultTo(toolDirName);
            }

            Preconditions.checkArgument(!"/var/lib/".equals($(homePath)));



            script = $.sys.script();

            $.sys.addRmLine(script, newRm($(homePath), $(currentVersionPath)).sudo());

            //todo change to static api
            script
                .line().sudo().addRaw("mkdir -p %s", $(homePath)).build()
                .line().sudo().addRaw("mv %s %s", $(buildPath) + "/" + $(versionName) + "/*", $(homePath)).build()
                .line().sudo().addRaw("ln -s %s %s", $(homePath), $(currentVersionPath)).build()
                .line().sudo().addRaw("chmod -R g+r,o+r %s", $(homeParentPath)).build()
                .line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", $(homePath)).build()
                .callback($.sshCallback());

            script.run();

            return script;
        }

        protected void shortCut(String newCommandName, String sourceExecutableName){
            Script script = $.sys.script();

            $.sys.addRmLine(script, newRm("/usr/bin/" + newCommandName).sudo());

            script
                .line().sudo().addRaw("ln -s %s/%s /usr/bin/%s", $(currentVersionPath), sourceExecutableName, newCommandName).build()
                .run();
        }


        public DependencyResult checkDeps(boolean throwException){
              return DependencyResult.OK;
        }



        protected DependencyResult verify() {
            final DependencyResult r = asInstalledDependency().checkDeps();

            if(r.ok()){
                $.log("%s has been installed", $(versionName));
            }
            return r;
        }
    }
}
