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
import com.google.common.base.Preconditions;


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

    public final TaskDef restartApp = new TaskDef("Restart App") {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return Task.nop();
        }
    };

    public final TaskDef deploy = new TaskDef("Deploy") {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    return runner.run(
                        update,
                        restartApp);
                }
            };
        }
    };

    public final TaskDef setup = new TaskDef("Generic Setup") {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, setup, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    final String[] dirs = {
                        $(bear.deployTo), $(bear.releasesPath), $(bear.vcsCheckoutPath),
                        $(bear.bearPath),
                        $(bear.sharedPath), $(bear.tempDirPath), $(bear.projectSharedPath),
                        $(bear.appLogsPath), $(bear.downloadDirPath),
                        $(bear.toolsInstallDirPath)
                    };

                    $.sys.sudo().mkdirs(dirs);

                    final String sshUser = $(bear.sshUsername);
                    final String appUser = $(bear.appUsername);

                    $.sys.sudo().chown(sshUser + "." + sshUser, true, dirs);
                    $.sys.sudo().chmod("g+w", true, dirs);

                    if (!appUser.equals(sshUser)) {
                        $.sys.sudo().chown(appUser + "." + appUser, true, $(bear.appLogsPath));
                    }

                    if ($(bear.autoInstallPlugins) || $(bear.verifyPlugins)) {
                        Iterable<Plugin> plugins = global.getGlobalPlugins();

                        for (Plugin<Task, ? extends TaskDef> plugin : plugins) {
                            if (plugin.getInstall().singleTask().createNewSession($, getParent()).asInstalledDependency().checkDeps().nok()) {
                                if ($(bear.autoInstallPlugins)) {
                                    $.log("plugin %s was not installed. installing it...", plugin);
                                    TaskResult run = runner.run(plugin.getInstall());
                                    if(!run.ok()){
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
    }.setSetupTask(true);

    public final TaskDef update = new TaskDef("Update") {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, update, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    return runner.run(new TransactionTaskDef(
                        updateCode,
                        createSymlink
                    ));
                }
            };
        }
    };

    public final TaskDef updateCode = new TaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, updateCode, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    return TaskResult.and(
                        runner.run($(bear.getStrategy)),
                        runner.run(finalizeTouchCode));
                }

                @Override
                protected void onRollback() {
                    $.sys.rm($(bear.releasePath));
                }
            };
        }
    };


    public final TaskDef finalizeTouchCode = new TaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, finalizeTouchCode, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.sys.chmod("g+w", true, $(bear.getLatestReleasePath));

                    return TaskResult.OK;
                }
            };
        }
    };

    public final TaskDef createSymlink = new TaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, createSymlink, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    return new TaskResult($.sys.link($(bear.getLatestReleasePath), $(bear.currentPath)));
                }

                @Override
                protected void onRollback() {
                    final String var = $(bear.getPreviousReleasePath);

                    if (var != null) {
                        $.sys.link(var, $(bear.currentPath));
                    }
                }
            };
        }
    };

    public final TaskDef vcsUpdate = new TaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, vcsUpdate, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    $.log("updating the project, please wait...");

                    if (!$.sys.exists($(bear.vcsBranchLocalPath))) {
                        return $(bear.vcs).checkout($(bear.revision), $(bear.vcsBranchLocalPath)).run();
                    } else {
                        return $(bear.vcs).sync($(bear.revision), $(bear.vcsBranchLocalPath)).run();
                    }
                }
            };
        }
    };

    public static TaskCallable<TaskDef> andThen(final TaskCallable<TaskDef>... callables){
        return new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                TaskResult lastResult = null;

                for (TaskCallable<TaskDef> callable : callables) {
                    lastResult = callable.call($, task, input);
                }

                return lastResult;
            }
        };
    }
}
