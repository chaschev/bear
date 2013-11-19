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

import bear.cli.Script;
import bear.console.ConsoleCallback;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.java.JavaPlugin;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import org.apache.commons.lang3.StringUtils;

import static bear.session.Variables.concat;
import static bear.session.Variables.dynamic;
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
public abstract class ZippedToolPlugin extends Plugin<Task, TaskDef<?>> {
    public final DynamicVariable<String>
        version = dynamic("version of the tool, a string which is return by a tool identifying it's version"),
        toolname = dynamic("this will be the name of home folder, i.e. maven, jdk"),
        toolDistrName = Variables.strVar("i.e. apache-tomcat").setEqualTo(toolname),
        versionName = concat(toolDistrName, "-", version).desc("i.e. apache-maven-3.0.5"),
        distrFilename = concat(versionName, ".tar.gz"),
        homeParentPath = Variables.newVar("/var/lib"),
        homePath = concat(homeParentPath, "/", toolname).desc("Tool root dir"),
        homeVersionPath = concat(homeParentPath, "/", versionName).desc("i.e. /var/lib/apache-maven-7.0.42"),
        currentVersionPath = concat(homeParentPath, "/", versionName),

        myDirPath,
        buildPath,

        distrWwwAddress = dynamic("distribution download address");

    public ZippedToolPlugin(GlobalContext global) {
        super(global);
        myDirPath = concat(bear.sharedPath, "/", toolname).desc("a path in a shared dir, i.e. /var/lib/<app-name>/shared/maven");
        buildPath = concat(myDirPath, "/build");
    }

    protected abstract class ZippedToolTaskDef<T extends ZippedTool> extends InstallationTaskDef<T>{

    }

    protected abstract class ZippedTool extends InstallationTask<TaskDef> {
        protected ZippedTool(Task<TaskDef> parent, TaskDef def, SessionContext $) {
            super(def, $, parent);

            addDependency(new Dependency(toString(), $).addCommands(
                "unzip -v | head -n 1",
                " wget --version | head -n 1"));
        }

        @Override
        protected DependencyResult exec(SessionTaskRunner runner) {
            throw new UnsupportedOperationException("todo implement!");
        }

        protected Script extractToHomeScript;

        protected abstract String extractVersion(String output);
        protected abstract String createVersionCommandLine();

        @Override
        public Dependency asInstalledDependency() {
            final Dependency dep = new Dependency($(toolDistrName) + " installation", $);

            dep.add(dep.new Command(
                $.sys.line().setVar("JAVA_HOME", $(global.getPlugin(JavaPlugin.class).homePath)).addRaw(createVersionCommandLine()),
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
            $.sys.rm($(buildPath));
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

            Preconditions.checkArgument(StringUtils.isNotBlank($(toolname)), "toolname is blank! you could delete /var/lib!");
            Preconditions.checkArgument(StringUtils.isNotBlank($(versionName)), "versionName is blank! you could delete /var/lib!");
            Preconditions.checkArgument(!"/var/lib/".equals($(homePath)));
            Preconditions.checkArgument(!"/var/lib/".equals($(homeVersionPath)));

            script = $.sys.script();

            script
                .line($.sys.rmLine(script.line().sudo(), $(homePath)))
                .line($.sys.rmLine(script.line().sudo(), $(homeVersionPath)))
                .line().sudo().addRaw("mv %s %s", $(buildPath) + "/" + $(versionName), $(homeParentPath)).build()
                .line().sudo().addRaw("ln -s %s %s", $(currentVersionPath), $(homePath)).build()
                .line().sudo().addRaw("chmod -R g+r,o+r %s", $(homePath)).build()
                .line().sudo().addRaw("chmod u+x,g+x,o+x %s/bin/*", $(homePath)).build();

            script.run(sshCallback());

            return script;
        }

        private ConsoleCallback sshCallback() {
            return SystemEnvironmentPlugin.passwordCallback($.var(bear.sshPassword));
        }

        protected void shortCut(String newCommandName, String sourceExecutableName){
            Script script = $.sys.script();

            script
                .line($.sys.rmLine(script.line().sudo(), "/usr/bin/" + newCommandName))
                .line().sudo().addRaw("ln -s %s/bin/%s /usr/bin/%s", $(homePath), sourceExecutableName, newCommandName).build()
                .run(sshCallback());
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
