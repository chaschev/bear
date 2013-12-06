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

import bear.core.GlobalContext;
import bear.plugins.Plugin;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Rollback extends Plugin<Task, TaskDef<?>> {
    public Rollback(GlobalContext global) {
        super(global);
    }

    @Override
    public InstallationTaskDef<InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }

//    public final TaskDef pointToPreviousRelease = new TaskDef() {
//
//        @Override
//        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
//            return new Task<TaskDef>(parent, this, $) {
//                @Override
//                protected TaskResult exec(SessionTaskRunner runner, Object input) {
//                    requirePreviousRelease($);
//
//                    Script script = $.sys.script();
//
//                    return script
//                        .line($.sys.addRmLine(script.line().sudo(), $(getBear().currentReleaseLinkPath)))
//                            .line().sudo().addRaw("ln -s %s %s", $(getBear().getPreviousReleasePath), $(getBear().currentReleaseLinkPath)).build()
//                            .run();
//                }
//            };
//        }
//    }.desc("[internal] Points the current symlink at the previous release.\n" +
//        "      This is called by the rollback sequence, and should rarely (if\n" +
//        "      ever) need to be called directly.");

//    public final TaskDef cleanup = new TaskDef() {
//        @Override
//        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
//            return new Task<TaskDef>(parent, this, $) {
//                @Override
//                protected TaskResult exec(SessionTaskRunner runner, Object input) {
//                        return $.sys.sendCommand(
//                            $.sys.line().sudo().addRaw("if [ `readlink #{%s}` != #{%s} ]; then #{try_sudo} rm -rf #{%s}; fi", true,
//                                $(getBear().currentReleaseLinkPath), $(getBear().releasePath), $(getBear().releasePath)));
//                }
//
//            };
//        }
//    };

//    public final TaskDef code = new TaskDef() {
//        @Override
//        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
//            return new Task<TaskDef>(parent, this, $) {
//                @Override
//                protected TaskResult exec(SessionTaskRunner runner, Object input) {
//                    return TaskResult.and(
//                        runner.run(pointToPreviousRelease),
//                        runner.run(cleanup));
//                }
//
//            };
//        }
//    };

//    public final TaskDef $default = new TaskDef() {
//        @Override
//        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
//            return new Task<TaskDef>(parent, this, $) {
//                @Override
//                protected TaskResult exec(SessionTaskRunner runner, Object input) {
//                    return TaskResult.and(
//                        runner.run(pointToPreviousRelease),
//                        runner.run(global.tasks.restartApp),
//                        runner.run(cleanup));
//                }
//            };
//        }
//    };


//    private void requirePreviousRelease(SessionContext $) {
//        if ($.var(bear.getPreviousReleasePath) != null) {
//            throw new RuntimeException("could not rollback the code because there is no prior release");
//        }
//    }
}
