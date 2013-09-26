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

import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.Plugin;
import cap4j.session.Result;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Rollback extends Plugin {
    public Rollback(GlobalContext global) {
        super(global);
    }

    @Override
    public void initPlugin() {

    }

    @Override
    public Task getSetup() {
        return null;
    }

    public final Task<TaskResult> pointToPreviousRelease = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            requirePreviousRelease($);

            return new TaskResult(
                system.script()
                    .line().sudo().addRaw("rm -r %s", $.var(cap.currentPath)).build()
                    .line().sudo().addRaw("ln -s %s %s", $.var(cap.getPreviousReleasePath), $.var(cap.currentPath)).build()
                    .run()
            );
        }
    }.desc("[internal] Points the current symlink at the previous release.\n" +
        "      This is called by the rollback sequence, and should rarely (if\n" +
        "      ever) need to be called directly.");

    public final Task<TaskResult> cleanup = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(
                system.run(
                    system.line().sudo().addRaw("if [ `readlink #{%s}` != #{%s} ]; then #{try_sudo} rm -rf #{%s}; fi",
                        $.var(cap.currentPath), $.var(cap.releasePath), $.var(cap.releasePath)))
            );
        }
    };

    public final Task<TaskResult> code = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(Result.and(
                runner.run(pointToPreviousRelease),
                runner.run(cleanup)));
        }
    };

    public final Task<TaskResult> $default = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(Result.and(
                runner.run(pointToPreviousRelease),
                runner.run(global.tasks.restartApp),
                runner.run(cleanup)));
        }
    };


    private void requirePreviousRelease(SessionContext $) {
        if ($.var(cap.getPreviousReleasePath) != null) {
            throw new RuntimeException("could not rollback the code because there is no prior release");
        }
    }
}
