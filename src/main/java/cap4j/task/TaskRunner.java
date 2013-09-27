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
import cap4j.core.SessionContext;
import cap4j.core.GlobalContext;
import cap4j.session.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static cap4j.session.Result.ERROR;
import static cap4j.session.Result.OK;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TaskRunner {
    private static final Logger logger = LoggerFactory.getLogger(TaskRunner.class);
    LinkedHashSet<TaskDef> tasksExecuted = new LinkedHashSet<TaskDef>();

    public SessionContext $;
    public final GlobalContext global;
    public final Cap cap;

    public TaskRunner(SessionContext $, GlobalContext global) {
        this.$ = $;
        this.global = global;
        this.cap = global.cap;
    }

    public Result run(TaskDef task) {
        return runWithDependencies(task);
    }

    public Result run(TaskDef... tasks) {
        return runMany((List) Arrays.asList(tasks));
    }

    public Result runMany(Iterable<TaskDef> tasks) {
        for (TaskDef task : tasks) {
            final Result result = runWithDependencies(task);

            if (result != OK) {
                return result;
            }
        }

        return OK;
    }


    //////

    protected Result runWithDependencies(TaskDef taskDef) {
        logger.info("starting task '{}'", taskDef.name);

        if (tasksExecuted.contains(taskDef)) {
            return OK;
        }

        return Result.and(
            runCollectionOfTasks(taskDef.dependsOnTasks, taskDef.name + ": depending tasks", false),
            runCollectionOfTasks(taskDef.beforeTasks, taskDef.name + ": before tasks", false),
            runMe(taskDef),
            runCollectionOfTasks(taskDef.afterTasks, taskDef.name + ": after tasks", false)
        );
    }

    private Result runMe(TaskDef task) {
        return runCollectionOfTasks(Collections.singletonList(task), task.name + ": running myself", true);
    }

    private Result runCollectionOfTasks(List<TaskDef> tasks, String desc, boolean thisIsMe) {
        if (!tasks.isEmpty() && !desc.isEmpty()) {
            logger.info(desc);
        }

        Result runResult = OK;

        for (TaskDef task : tasks) {
            if (!task.roles.isEmpty() && !task.hasRole($.sys.getRoles())) {
                continue;
            }

            runResult = _runSingleTask(task, thisIsMe);

            if (runResult != OK) {
                break;
            }
        }
        return runResult;
    }

    private Result _runSingleTask(TaskDef task, boolean thisIsMe) {
        Result result = null;
        try {
            if (!thisIsMe) {
                result = runWithDependencies(task);
            } else {
                result = task.newSession($).run(this).result;
            }
        } catch (Exception ignore) {
            logger.error("", ignore);
        }

        Result runResult;

        if (result == null || result != OK) {
            runResult = ERROR;
        } else {
            runResult = OK;
        }

        return runResult;
    }

    public void runRollback(TaskDef task) {
        task.newSession($).onRollback();
    }
}
