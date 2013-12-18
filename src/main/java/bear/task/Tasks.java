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
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import chaschev.lang.OpenStringBuilder;
import com.google.common.base.Preconditions;

import java.util.Arrays;

import static bear.plugins.sh.DirsInput.mk;
import static bear.plugins.sh.DirsInput.perm;


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

    public final TaskDef restartApp = new TaskDef<Task>("Restart App", new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return Task.nop();
        }
    });

    public final InstallationTaskDef<InstallationTask> setup = new InstallationTaskDef<InstallationTask>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, setup, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
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

                    $.sys.mkdirs(mk(dirs).sudo().withPermissions("g+w").withUser(sshUser + "." + sshUser));


//                    $.sys.chown(sshUser + "." + sshUser, true, dirs);
//                    $.sys.chmod("g+w", true, dirs);

                    if (!appUser.equals(sshUser)) {
                        //this part might be changed
                        $.sys.permissions(perm($(bear.appLogsPath)).sudo().withUser(appUser + "." + appUser));
                    }

                    if ($(bear.autoInstallPlugins) || $(bear.verifyPlugins)) {
                        Iterable<Plugin> plugins = global.getGlobalPlugins();

                        for (Plugin<Task, ? extends TaskDef> plugin : plugins) {
                            InstallationTaskDef<? extends InstallationTask> installTask = plugin.getInstall();
                            if (installTask.singleTaskSupplier().createNewSession($, getParent(), (TaskDef)installTask).asInstalledDependency().checkDeps().nok()) {
                                if ($(bear.autoInstallPlugins)) {
                                    $.log("plugin %s was not installed. installing it...", plugin);
                                    TaskResult run = runner.run(installTask);
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
    })  .setName("Generic Setup")
        .setSetupTask(true);


    public final TaskDef vcsUpdate = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, vcsUpdate, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
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

    public static TaskCallable<TaskDef> andThen(final TaskCallable<TaskDef>... callables) {
        int nullCount = 0;
        int lastNullIndex = -1;
        for (int i = 0; i < callables.length; i++) {
            TaskCallable<TaskDef> callable = callables[i];
            if (callable == null) {
                nullCount++;
                lastNullIndex = i;
            }
        }

        if (nullCount == 1) {
            return callables[lastNullIndex];
        }

        return new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                TaskResult lastResult = null;

                for (TaskCallable<TaskDef> callable : callables) {
                    if (callable == null) continue;
                    lastResult = callable.call($, task, input);
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

    public static <TASK extends Task> SingleTaskSupplier<TASK> newSingleTask(final TaskCallable<TaskDef> taskCallable) {
        return new SingleTaskSupplier<TASK>() {
            @Override
            public TASK createNewSession(SessionContext $, Task parent, TaskDef<TASK> def) {
                return (TASK) new Task(parent,  taskCallable);
            }
        };
    }
}
