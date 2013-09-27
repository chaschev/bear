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

import cap4j.core.SessionContext;
import cap4j.session.GenericUnixRemoteEnvironment;
import cap4j.session.Result;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TransactionTaskDef extends TaskDef {
    private static final Logger logger = LoggerFactory.getLogger(GenericUnixRemoteEnvironment.class);

    List<TaskDef> tasks;

    public TransactionTaskDef(List<TaskDef> tasks) {
        this.tasks = tasks;
    }

    public TransactionTaskDef(TaskDef... tasks) {
        this.tasks = (ArrayList) Lists.newArrayList(tasks);
        name = "transaction of " + tasks.length + " tasks";
    }

    @Override
    public Task newSession(SessionContext $) {
        return new Task(this, $) {
            @Override
            protected TaskResult run(TaskRunner runner) {
                Result result = null;
                try {
                    result = runner.runMany(tasks);
                } catch (Exception e) {
                    logger.warn("", e);
                    result = Result.ERROR;
                }

                if (result != Result.OK) {
                    //let's keep it simple
                    for (TaskDef task : tasks) {
                        runner.runRollback(task);
                    }
                }

                return new TaskResult(result);
            }
        };
    }
}
