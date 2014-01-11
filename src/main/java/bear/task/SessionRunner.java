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

import bear.context.HavingContext;
import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SessionRunner extends HavingContext<SessionRunner, SessionContext>{
    private static final Logger logger = LoggerFactory.getLogger(SessionRunner.class);
    LinkedHashSet<TaskDef> tasksExecuted = new LinkedHashSet<TaskDef>();

    public final GlobalContext global;
    public final Bear bear;
    public Function<Task<Object, TaskResult<?>>, Task<Object, TaskResult<?>>> taskPreRun; //a hack

    private TaskResult<?> myLastResult = TaskResult.OK;
    private TaskResult<?> myLastRollbackResult = TaskResult.OK;

    private State state = State.NOT_RUNNING;

    //
    private static enum State{
        NOT_RUNNING, // -> RUNNING
        RUNNING,     // -> ROLLBACK, FINISHED
        ROLLBACK,    // -> FINISHED
        FINISHED
    }

    public SessionRunner(SessionContext $, GlobalContext global) {
        super($);
        this.global = global;
        this.bear = global.bear;
    }

    public TaskResult<?> run(TaskDef task) {
        return runWithDependencies(task);
    }

    public TaskResult<?> run(TaskDef... tasks) {
        return run(null, tasks);
    }

    public TaskResult<?> run(Object input, TaskDef... tasks) {
//        this.input = input;

        for (TaskDef task : (Iterable<TaskDef>) (List) Arrays.asList(tasks)) {
            final TaskResult<?> result = runWithDependencies(task);

            if (!result.ok()) {
                return result;
            }
        }

        return TaskResult.OK;
    }


    // a protected entry point

    protected TaskResult<?> runWithDependencies(TaskDef taskDef) {
        if(state == State.NOT_RUNNING) state = State.RUNNING;

        logger.info("starting task '{}'", taskDef.name);

        if (tasksExecuted.contains(taskDef)) {
            return TaskResult.OK;
        }

        TaskResult<?> last =
            runCollectionOfTasks(taskDef.dependsOnTasks, taskDef.name + ": depending tasks", false);

        TaskResult<?> myResult = TaskResult.OK;

        last = last.nok() ? last : runCollectionOfTasks(taskDef.beforeTasks, taskDef.name + ": before tasks", false);
        last = last.nok() ? last : (myResult = runMe(taskDef));
        last = last.nok() ? last : runCollectionOfTasks(taskDef.afterTasks, taskDef.name + ": after tasks", false);

        if(state != State.ROLLBACK){
            myLastResult = myResult;
        } else{
            myLastRollbackResult = myResult;
        }

        if(!myResult.ok()){
            logger.warn("running rollback for task: {}", taskDef);

            state = State.ROLLBACK;

            taskDef.runRollback(this);
        }

        return last;
    }

    private TaskResult<?> runMe(TaskDef task) {
        return runCollectionOfTasks(Collections.singletonList(task), task.name + ": running myself", true);
    }

    private TaskResult<?> runCollectionOfTasks(List<TaskDef> tasks, String desc, boolean thisIsMe) {
        if (!tasks.isEmpty() && !desc.isEmpty()) {
            logger.debug(desc);
        }

        TaskResult<?> runResult = TaskResult.OK;

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

    private TaskResult<?> _runSingleTask(TaskDef<Object, TaskResult<?>> taskDef, boolean thisIsMe) {
        TaskResult<?> result = TaskResult.OK;
        try {
            if (!thisIsMe) {
                result = runWithDependencies(taskDef);
            } else {
                List<Task<Object, TaskResult<?>>> tasks = taskDef.createNewSessionsAsList($, $.getCurrentTask());

                for (Task taskSession : tasks) {
                    if(taskSession == Task.nop()) continue;

                    if(taskPreRun != null){
                        taskSession = taskPreRun.apply(taskSession);
                    }

                    if(taskSession.getInput() == null){
                        taskSession.setInput(myLastResult);
                    }

                    result = runSession(taskSession);

                    if(!result.ok()){
                        return result;
                    }
                }
            }
        } catch (BearException e){
            throw e;
        } catch (Exception e) {
            logger.error("", e);
            result = TaskResult.of(e);
        }

        return result;
    }

    public <I, O extends TaskResult<?>> O runSession(Task<I, O> taskSession) {
        return runSession(taskSession, null);
    }

    public <I, O extends TaskResult<?>> O runSession(Task<I, O> taskSession, I input) {
        {
            SessionContext taskCtx = taskSession.$();
            boolean sameContexts = taskCtx == null || taskCtx == $;
            Preconditions.checkArgument(sameContexts, "" +
                "contexts are different for task sessions: %s vs %s", taskCtx == null ? null : taskCtx.getName(), ($ == null ? null : $.getName()));
        }

        TaskResult<?> result = TaskResult.OK;

        // todo this line was added for dep checks and might be needed
        // to be moved lower if dep checks use their own session
        $.setCurrentTask(taskSession);

        if($(bear.checkDependencies)){
            result = taskSession.getDependencies().check();

            if(!result.ok() && $(bear.autoInstallPlugins)){
                result = taskSession.getDependencies().tryInstall();

                if(result.ok()){
                    result = taskSession.getDependencies().check();
                }
            }
        }

//        $.setCurrentTask(taskSession);

        O myResult = null;

        if(result.ok()){
            taskSession.beforeExec();
            myResult = taskSession.run(this, input);
            taskSession.afterExec();
        } else{
            myResult = (O) result;
        }

        return myResult;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SessionRunner{");
        if($ != null) sb.append("name=").append($.getName());
        sb.append('}');
        return sb.toString();
    }

    public TaskResult<?> getMyLastResult() {
        return myLastResult;
    }

    public TaskResult<?> getMyLastRollbackResult() {
        return myLastRollbackResult;
    }
}
