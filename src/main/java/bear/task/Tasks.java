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

package bear.task;

import bear.core.Bear;
import bear.core.BearProject;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import chaschev.lang.OpenStringBuilder;
import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.List;


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Tasks {
    Bear bear;
    GlobalContext global;

    public Tasks(GlobalContext global) {
        this.global = global;
        this.bear = global.bear;
        Preconditions.checkNotNull(bear);
    }

    public final TaskDef restartApp = new TaskDef<Object, TaskResult>("Restart App", new SingleTaskSupplier<Object, TaskResult>() {
        @Override
        public Task<Object, TaskResult> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<Object, TaskResult> def) {
            return Task.nop();
        }
    });

    public final InstallationTaskDef<InstallationTask> setup = new InstallationTaskDef<InstallationTask>(new SetupTaskSupplier())  .setName("Generic Setup")
        .setSetupTask(true);


    public final TaskDef vcsUpdate = new TaskDef<Object, TaskResult>(new SingleTaskSupplier<Object, TaskResult>() {
        @Override
        public Task<Object, TaskResult> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<Object, TaskResult> def) {
            return new Task<Object, TaskResult>(parent, vcsUpdate, $) {
                @Override
                protected TaskResult exec(SessionRunner runner) {
                    $.log("updating the project, please wait...");

                    if (!$.sys.exists($(bear.vcsBranchLocalPath))) {
                        return $(bear.vcs).checkout($(bear.revision), $(bear.vcsBranchLocalPath)).run();
                    } else {
                        return $(bear.vcs).sync($(bear.revision), $(bear.vcsBranchLocalPath)).run();
                    }
                }
            };
        }
    });

    public static TaskCallable<Object, TaskResult> andThen(final TaskCallable<Object, TaskResult>... callables) {
        int nullCount = 0;
        int lastNullIndex = -1;
        for (int i = 0; i < callables.length; i++) {
            TaskCallable<Object, TaskResult> callable = callables[i];
            if (callable == null) {
                nullCount++;
                lastNullIndex = i;
            }
        }

        if (nullCount == 1) {
            return callables[lastNullIndex];
        }

        return new TaskCallable<Object, TaskResult>() {
            @Override
            public TaskResult call(SessionContext $, Task<Object, TaskResult> task) throws Exception {
                TaskResult lastResult = null;

                for (TaskCallable<Object, TaskResult> callable : callables) {
                    if (callable == null) continue;
                    lastResult = callable.call($, task);
                }

                return lastResult;
            }
        };
    }

    public static TaskResult and(TaskResult... results) {
        return and(Arrays.asList(results));
    }

    public static TaskResult and(Iterable<? extends TaskResult> results) {
        TaskResult last = TaskResult.OK;

        OpenStringBuilder sb = new OpenStringBuilder();

        Throwable lastException = null;

        boolean ok = true;

        for (TaskResult result : results) {
            last = result;
            if (!result.ok()) {
                ok = false;

                if (result.exception.isPresent()) {
                    lastException = result.exception.get();
                    sb.append(result.exception.get().toString()).append("\n");
                }
            }
        }

        if (ok) return last;

        Exception ex = new Exception(sb.trim().toString());

        if (lastException != null) {
            ex.setStackTrace(lastException.getStackTrace());
        }

        return new TaskResult(ex);
    }

    public static <I, O extends TaskResult> SingleTaskSupplier<I, O> newSingleSupplier(final TaskCallable<I, O> taskCallable) {
        return new SingleTaskSupplier<I, O>() {
            @Override
            public Task<I, O> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<I, O> def) {
                return new Task<I, O>(parent, taskCallable);
            }
        };
    }

    public static <I, O extends TaskResult> TaskCallable<I, O> nopCallable(){
        return (TaskCallable) TaskCallable.NOP;
    }

    private class SetupTaskSupplier implements SingleTaskSupplier<BearProject, TaskResult> {
        @Override
        public Task<BearProject, TaskResult> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<BearProject, TaskResult> def) {
            return new Task<BearProject, TaskResult>(parent, setup, $) {
                @Override
                protected TaskResult exec(SessionRunner runner) {
                    $.putConst(bear.installationInProgress, true);

                    final String[] dirs = {
                        $(bear.applicationPath), $(bear.vcsCheckoutPath),
                        $(bear.bearPath),
                        $(bear.sharedPath), $(bear.tempDirPath), $(bear.projectSharedPath),
                        $(bear.appLogsPath), $(bear.downloadDirPath),
                        $(bear.toolsInstallDirPath)
                    };

                    final String sshUser = $(bear.sshUsername);
                    final String appUser = $(bear.appUsername);

                    $.sys.mkdirs(dirs).sudo().withPermissions("g+w").withUser(sshUser + "." + sshUser).run();


//                    $.sys.chown(sshUser + "." + sshUser, true, dirs);
//                    $.sys.chmod("g+w", true, dirs);

                    if (!appUser.equals(sshUser)) {
                        //this part might be changed
                        $.sys.permissions($(bear.appLogsPath)).sudo().withUser(appUser + "." + appUser).run();
                    }

                    if ($(bear.autoInstallPlugins) || $(bear.verifyPlugins)) {
                        List<Plugin<Task, TaskDef>> plugins = getInput() == null ? global.getOrderedPlugins()
                                : getInput().getAllOrderedPlugins();

                        for (Plugin<Task, ? extends TaskDef> plugin : plugins) {
                            InstallationTaskDef<? extends InstallationTask> installTaskDef = plugin.getInstall();
                            InstallationTask session = (InstallationTask) installTaskDef.singleTaskSupplier().createNewSession($, (Task)getParent(), installTaskDef);
                            if (session.asInstalledDependency().checkDeps().nok()) {
                                if ($(bear.autoInstallPlugins)) {
                                    $.log("plugin %s was not installed. installing it...", plugin);
                                    TaskResult run = runner.run(installTaskDef);
                                    if (!run.ok()) {
                                        $.error("could not install %s:%n%s", plugin, run);
                                        break;
                                    }
                                } else {
                                    $.warn("plugin %s was not installed (autoSetup is off)", plugin);
                                }
                            }
                        }
                    }

                    return TaskResult.OK;
                }
            };
        }
    }
}
