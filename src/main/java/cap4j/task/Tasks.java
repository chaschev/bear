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

package cap4j.task;

import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.Plugin;
import cap4j.scm.VcsCLIPlugin;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.time.StopWatch;


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Tasks {
    Cap cap;
    GlobalContext global;

    public Tasks(GlobalContext global) {
        this.global = global;
        this.cap = global.cap;
        Preconditions.checkNotNull(cap);
    }

    public final TaskDef restartApp = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return Task.nop();
        }
    };

    public final TaskDef deploy = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(deploy, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    return runner.run(
                        update,
                        restartApp);
                }
            };
        }
    };

    public final TaskDef setup = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(setup, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    final String[] dirs = {
                        $(cap.deployTo), $(cap.releasesPath), $(cap.vcsCheckoutPath),
                        $(cap.sharedPath), $(cap.appLogsPath)
                    };

                    $.sys.sudo().mkdirs(dirs);

                    final String sshUser = $(cap.sshUsername);
                    final String appUser = $(cap.appUsername);

                    $.sys.sudo().chown(sshUser + "." + sshUser, true, dirs);
                    $.sys.sudo().chmod("g+w", true, dirs);

                    if (!appUser.equals(sshUser)) {
                        $.sys.sudo().chown(appUser + "." + appUser, true, $(cap.appLogsPath));
                    }

                    if ($.var(cap.verifyPlugins)) {
                        for (Plugin plugin : global.getGlobalPlugins()) {
                            if (plugin.getInstall().newSession($).asInstalledDependency().checkDeps().nok()) {
                                if ($(cap.autoInstallPlugins)) {
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

    public final TaskDef update = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(update, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
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
        public Task newSession(SessionContext $) {
            return new Task(updateCode, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    return TaskResult.and(
                            $(cap.newStrategy).deploy(),
                            runner.run(finalizeTouchCode));
                }

                @Override
                protected void onRollback() {
                    $.sys.rm($(cap.releasesPath));
                }
            };
        }
    };


    public final TaskDef finalizeTouchCode = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(finalizeTouchCode, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    $.sys.chmod("g+w", true, $(cap.getLatestReleasePath));

                    return TaskResult.OK;
                }
            };
        }
    };

    public final TaskDef createSymlink = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(createSymlink, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    return new TaskResult($.sys.link($(cap.getLatestReleasePath), $(cap.currentPath)));
                }

                @Override
                protected void onRollback() {
                    final String var = $(cap.getPreviousReleasePath);

                    if (var != null) {
                        $.sys.link(var, $(cap.currentPath));
                    }
                }
            };
        }
    };

    public final TaskDef vcsUpdate = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(vcsUpdate, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    $.log("updating the project, please wait...");

                    StopWatch sw = new StopWatch();
                    sw.start();

                    final VcsCLIPlugin.Session vcsCLI = $(cap.vcs);

                    final String destPath = $(cap.vcsBranchLocalPath);

                    final cap4j.cli.Script line;

                    if (!$.sys.exists(destPath)) {
                        line = vcsCLI.checkout($(cap.revision), destPath, VcsCLIPlugin.emptyParams());
                    } else {
                        line = vcsCLI.sync($(cap.revision), destPath, VcsCLIPlugin.emptyParams());
                    }

                    line.timeoutMs(600 * 1000);

                    $.sys.run(line, vcsCLI.passwordCallback());

                    $.log("done updating in %s", sw);

                    return TaskResult.OK;
                }
            };
        }
    };
}
