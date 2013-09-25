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

package cap4j.examples;

import cap4j.core.GlobalContext;
import cap4j.core.Stage;
import cap4j.session.*;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;

import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Ex1Basic {
    public static void main(String[] args) {

        final Stage pacDev = new Stage("pac-dev", GlobalContext.getInstance())
            .add(newUnixRemote("", "chaschev", "1", "192.168.25.66", GlobalContext.getInstance()));

        final Task<TaskResult> testTask = new Task<TaskResult>() {
            {

            }

            @Override
            protected TaskResult run(TaskRunner runner) {
                system.copy("src", "dest");
//                system.runForEnvironment("linux", new SystemEnvironments.EnvRunnable() {
//                    @Override
//                    public Result run(SystemEnvironment system) {
//                        return system.run(new BaseScm.CommandLine().a("echo",  "blahblahblah")).result;
//                    }
//                });

                return new TaskResult(Result.OK);
            }
        };

        pacDev.runTask(testTask);
    }
}
