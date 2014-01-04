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

import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.java.JavaPlugin;
import bear.plugins.sh.CommandLine;
import bear.plugins.sh.Script;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.session.DynamicVariable;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import org.slf4j.LoggerFactory;

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
public class ZippedToolPlugin extends Plugin<TaskDef<Object, TaskResult<?>>> {


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

    protected class ZippedToolTaskDef<T extends ZippedTool> extends InstallationTaskDef<T> {

        public ZippedToolTaskDef(SingleTaskSupplier singleTaskSupplier) {
            super(singleTaskSupplier);
        }
    }


    public static class SystemDependencyPlugin extends Plugin<TaskDef<Object, TaskResult<?>>> {
        public SystemDependencyPlugin(GlobalContext global) {
            super(global);
        }

        @Override
        public InstallationTaskDef<? extends InstallationTask> getInstall() {
            return new InstallationTaskDef<InstallationTask>(new NamedSupplier<Object, TaskResult<?>>("zippedTool.nopInstall", new SingleTaskSupplier<Object, TaskResult<?>>() {
                @Override
                public Task<Object, TaskResult<?>> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<Object, TaskResult<?>> def) {
                    return new Task<Object, TaskResult<?>>(parent, new TaskCallable<Object, TaskResult<?>>() {
                        @Override
                        public TaskResult<?> call(SessionContext $, Task<Object, TaskResult<?>> task) throws Exception {
//                            $.sys.getPackageManager()
                            return TaskResult.OK;
                        }
                    });
                }
            }));
        }
    }

    protected abstract class ZippedTool extends InstallationTask<InstallationTaskDef> {
        protected ZippedTool(Task<Object, TaskResult<?>> parent, InstallationTaskDef def, SessionContext $) {
            super(parent, def, $);

            addDependency(new Dependency(toString(), $)
                .addCommands(
                    "unzip -v | head -n 1",
                    "wget --version | head -n 1"
                )
                .setInstaller(new NamedCallable<Object, TaskResult<?>>("zippedTool.basicDeps", new TaskCallable<Object, TaskResult<?>>() {
                    @Override
                    public TaskResult<?> call(SessionContext $, Task<Object, TaskResult<?>> task) throws Exception {
                        SystemEnvironmentPlugin.PackageManager manager = $.sys.getPackageManager();

                        manager.installPackage("unzip").throwIfError();
                        manager.installPackage("wget").throwIfError();

                        return TaskResult.OK;
                    }
                }))
            );
        }

        @Override
        protected TaskResult<?> exec(SessionRunner runner) {
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

            if (java.isPresent()) {
                try {
                    line.setVar("JAVA_HOME", $(java.get().homePath));
                } catch (Fun.UndefinedException e) {
                    LoggerFactory.getLogger("log").debug("ignoring JAVA_HOME as the version is not set");
                }
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

        protected void clean() {
            $.sys.rm($(buildPath)).run();
            $.sys.mkdirs($(buildPath)).run();
        }

        protected void download() {
            if (!$.sys.exists($.sys.joinPath($(myDirPath), $(distrFilename)))) {
                String url = $(distrWwwAddress);
                CommandLineResult<?> run = $.sys.script()
                    .cd($(myDirPath))
                    .line().timeoutMin(60).addRaw("wget %s", url).build()
                    .run();

                Predicate<String> errorPredicate = or(contains("404 Not Found"), contains("ERROR"));

                if (errorPredicate.apply(run.output)) {
                    throw new RuntimeException("Error during download of " + url +
                        ": " + find(on('\n').split(run.output), errorPredicate));
                }
            }
        }


        protected void extractToHomeDir() {
            final String distrName = $(distrFilename);

            Script<CommandLineResult<?>, Script> script = $.sys.script()
                .cd($(buildPath))
                .timeoutSec(60);

            if (distrName.endsWith("tar.gz")) {
                script.line().addRaw("tar xvfz ../%s", distrName).build();
            } else if (distrName.endsWith("gz")) {
                script.line().addRaw("tar xvf ../%s", distrName).build();
            } else if (distrName.endsWith("zip")) {
                script.line().addRaw("unzip ../%s", distrName).build();
            } else if (distrName.endsWith("bin")) {
                script
                    .add($.sys.permissions(distrName).withPermissions("u+x").asLine())
                    .line().addRaw("./%s", distrName).build();
            } else {
                throw new IllegalArgumentException("unsupported archive type: " + distrName);
            }

            script
                .timeoutForInstallation()
                .run();

            String toolDirName = $.sys.capture(String.format("cd %s && ls -w 1", $(buildPath))).trim();

            if (!toolDirName.equals($(versionName))) {
                $.warn("tool version name is not equal to tool dir name: (expected) %s vs (actual) %s. setting new tool name to %s", $(versionName), toolDirName, toolDirName);
                versionName.defaultTo(toolDirName);
            }

            Preconditions.checkArgument(!"/var/lib/".equals($(homePath)));

//            script = $.sys.script();

            $.sys.rm($(homePath), $(currentVersionPath)).run();

            $.sys.mkdirs($(homePath)).run();

            $.sys.move($(buildPath) + "/" + $(versionName) + "/*").to($(homePath)).run();

            $.sys.link($(currentVersionPath)).toSource($(homePath)).run();

            $.sys.permissions($(homeParentPath)).withPermissions("g+r,o+r").run();

            $.sys.permissions($(homePath) + "/bin/*").withPermissions("u+x,g+x,o+x").run();
        }

        protected void shortCut(String newCommandName, String sourceExecutableName) {
            $.sys.link("/usr/bin/" + newCommandName).toSource($(currentVersionPath) + "/" + sourceExecutableName).sudo().run();
// Script script = $.sys.script();

//            $.sys.rm("/usr/bin/" + newCommandName).sudo().run();
//            script
//                .line().sudo().addRaw("ln -s %s/%s /usr/bin/%s", $(currentVersionPath), sourceExecutableName, newCommandName).build()
//                .run();
        }


        public DependencyResult checkDeps(boolean throwException) {
            return DependencyResult.OK;
        }


        protected DependencyResult verify() {
            final DependencyResult r = asInstalledDependency().checkDeps();

            if (r.ok()) {
                $.log("%s has been installed", $(versionName));
            }
            return r;
        }
    }
}
