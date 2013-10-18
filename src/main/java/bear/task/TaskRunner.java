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
import bear.core.SessionContext;
import bear.core.GlobalContext;
import bear.session.Result;
import bear.vcs.CommandLineResult;
import bear.plugins.HavingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TaskRunner extends HavingContext<TaskRunner, SessionContext>{
    private static final Logger logger = LoggerFactory.getLogger(TaskRunner.class);
    LinkedHashSet<TaskDef> tasksExecuted = new LinkedHashSet<TaskDef>();

    public final GlobalContext global;
    public final Bear bear;

    public TaskRunner(SessionContext $, GlobalContext global) {
        super($);
        this.global = global;
        this.bear = global.bear;
    }

    public TaskResult run(TaskDef task) {
        return runWithDependencies(task);
    }

    public TaskResult run(TaskDef... tasks) {
        return runMany((List) Arrays.asList(tasks));
    }

    public TaskResult runMany(Iterable<TaskDef> tasks) {
        for (TaskDef task : tasks) {
            final TaskResult result = runWithDependencies(task);

            if (result != TaskResult.OK) {
                return result;
            }
        }

        return TaskResult.OK;
    }


    //////

    protected TaskResult runWithDependencies(TaskDef taskDef) {
        logger.info("starting task '{}'", taskDef.name);

        if (tasksExecuted.contains(taskDef)) {
            return TaskResult.OK;
        }

        TaskResult last =
            runCollectionOfTasks(taskDef.dependsOnTasks, taskDef.name + ": depending tasks", false);

        last = last.nok() ? last : runCollectionOfTasks(taskDef.beforeTasks, taskDef.name + ": before tasks", false);
        last = last.nok() ? last : runMe(taskDef);
        last = last.nok() ? last : runCollectionOfTasks(taskDef.afterTasks, taskDef.name + ": after tasks", false);

        return last;
    }

    private TaskResult runMe(TaskDef task) {
        return runCollectionOfTasks(Collections.singletonList(task), task.name + ": running myself", true);
    }

    private TaskResult runCollectionOfTasks(List<TaskDef> tasks, String desc, boolean thisIsMe) {
        if (!tasks.isEmpty() && !desc.isEmpty()) {
            logger.info(desc);
        }

        TaskResult runResult = TaskResult.OK;

        for (TaskDef task : tasks) {
            if (!task.roles.isEmpty() && !task.hasRole($.sys.getRoles())) {
                continue;
            }

            runResult = _runSingleTask(task, thisIsMe);

            if (runResult.nok()) {
                break;
            }
        }
        return runResult;
    }

    private TaskResult _runSingleTask(TaskDef taskDef, boolean thisIsMe) {
        TaskResult result = null;
        try {
            if (!thisIsMe) {
                result = runWithDependencies(taskDef);
            } else {
                Task<TaskDef> taskSession = taskDef.newSession($, $.getCurrentTask());

                if($(bear.checkDependencies)){
                    DependencyResult depsResult = taskSession.getDependencies().check();

                    if(depsResult.nok()){
                        return depsResult;
                    }
                }

                $.setCurrentTask(taskSession);

                result = taskSession.run(this);
            }
        }
        catch (BearException e){
            throw e;
        }
        catch (DependencyException e){
            throw e;
        }
        catch (Exception e) {
            logger.error("", e);
            result = new CommandLineResult(e.toString(), Result.ERROR);
        }

        return result;
    }

    public void runRollback(TaskDef task) {
        task.newSession($, $.getCurrentTask()).onRollback();
    }
}
