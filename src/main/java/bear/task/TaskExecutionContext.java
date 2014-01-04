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

import bear.console.AbstractConsoleCommand;
import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.of;

/**
 * This is a field in `Task`.
 *
 * executionEntries:
 *  - direct sub-tasks of this task (tasks which are in the body of this task (run during this task execution)
 *  - commands run on the level of this task
 *
 * Me
 * executionEntries: Task,     Task,   Command,    Task
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TaskExecutionContext extends ExecContext<TaskExecutionContext> {
    private static final Logger logger = LoggerFactory.getLogger(TaskExecutionContext.class);

//    TaskExecutionEntry selfEntry;
    List<ExecContext> execEntries = new ArrayList<ExecContext>();
    public TaskResult<?> taskResult;
    protected Task<Object, TaskResult<?>> task;


    public TaskExecutionContext(SessionContext $, Task<Object, TaskResult<?>> task) {
        super($, getParentContext(task));
        this.task = task;

        Task.wrongThreadCheck($);

//        selfEntry = new TaskExecutionEntry(task.getParentEntry(), task);
    }

    private static TaskExecutionContext getParentContext(@Nonnull Task<Object, TaskResult<?>> task) {
        Task<Object, TaskResult<?>> parent = task.getParent();

        if (parent == null) return null;

        return parent.getExecutionContext();
    }

    //null when there is an exception
    protected ExecContext findEntryByTask(Task<Object, TaskResult<?>> task) {
        for (ExecContext e : execEntries) {
            if (e instanceof TaskExecutionContext) {
                TaskExecutionContext context = (TaskExecutionContext) e;
                if(context.task == task) return e;
            }
        }

        return null;
    }

    protected ExecContext findEntryByCommand(AbstractConsoleCommand command) {
//        for (ExecutionEntry e : executionEntries) {
//            if (e instanceof CommandExecutionEntry) {
//                if (((CommandExecutionEntry) e).command == command) return e;
//            }
//        }

        for (ExecContext e : execEntries) {
            if (e instanceof CommandContext) {
                CommandContext context = (CommandContext) e;
                if(context.command==command) return e;
            }
        }

        return null;
    }

    public void addNewSubTask(Task<Object, TaskResult<?>> subTask) {
//        executionEntries.add(new TaskExecutionEntry(subTask.getParentEntry(), subTask));
        execEntries.add(new TaskExecutionContext($, subTask));
    }

    public void onEndSubTask(Task<Object, TaskResult<?>> task, TaskResult<?> result) {
        ExecContext entry = findEntryByTask(task);
        if(entry != null){
            entry.onEnd(result);
        }
    }

    public <T extends CommandLineResult<?>> void addNewCommand(AbstractConsoleCommand<T> command) {
        CommandContext co = new CommandContext($, this, command);

        execEntries.add(co);

        $.getExecutionContext().currentCommand.defaultTo(co);
    }

    public <T extends CommandLineResult<?>> void onEndCommand(AbstractConsoleCommand<T> command, T result) {
        ExecContext execContext = findEntryByCommand(command);
        if(execContext == null){
            logger.warn("");
        }else{
            execContext.onEnd(result);
        }
    }

    @Nonnull
    public Optional<ExecContext> getFirstEntry() {
        if (execEntries.isEmpty()) {
            return absent();
        }

        return of(execEntries.get(0));
    }

    @Nonnull
    public Optional<ExecContext> getLastEntry() {
        if (execEntries.isEmpty()) return absent();

        return of(execEntries.get(execEntries.size() - 1));
    }

    public boolean isEmpty() {
        return !getFirstEntry().isPresent();
    }

    @Override
    public boolean hasStarted(){
        return getFirstEntry().isPresent();
    }

    public DateTime getStartedAt() {
        if(startedAt != null){
            return startedAt;
        }

        Optional<ExecContext> e = getFirstEntry();

        if (e.isPresent()) {
            return e.get().getStartedAt();
        }

        throw new IllegalStateException("not started");
    }

    public long getDuration() {
//        Optional<ExecContext> firstEntry = getFirstEntry();
//
//        if (!firstEntry.isPresent() || !firstEntry.get().hasStarted()) {
//            return 0;
//        }
//
//        Optional<ExecContext> lastEntry = getLastEntry();
//
//        DateTime finishedAt = lastEntry.isPresent() ? null : lastEntry.get().getFinishedAt();

        DateTime finishedAt = this.finishedAt == null ? new DateTime() : this.finishedAt;



        return finishedAt.getMillis() - getStartedAt().getMillis();
    }

    public boolean isRunning() {
        return taskResult == null;
    }

    public boolean isFinished() {
        return !isRunning();
    }

    public static interface ExecutionVisitor{
        boolean visit(ExecContext<?> execContext);
    }

    public Optional<? extends TaskResult> lastResult(){
        if(taskResult != null){
            return of(taskResult);
        }

        TaskExecutionContext last = null;

        for (ExecContext execEntry : execEntries) {
            if (execEntry instanceof TaskExecutionContext) {
                last = (TaskExecutionContext) execEntry;
            }
        }

        if(last != null){
            return last.lastResult();
        }

        return absent();
    }

    public Optional<? extends TaskResult> findResult(final TaskDef<Object, TaskResult<?>> def){
        final TaskResult<?>[] r = new TaskResult<?>[1];

        visit(new ExecutionVisitor() {
            @Override
            public boolean visit(ExecContext<?> execContext) {
                if (execContext instanceof TaskExecutionContext) {
                    TaskExecutionContext context = (TaskExecutionContext) execContext;
                    if(context.task.getDefinition() == def){
                        r[0] = context.task.getExecutionContext().taskResult;
                        return false;
                    }
                }

                return true;
            }
        });

        return Optional.fromNullable(r[0]);
    }

    @Override
    public boolean visit(ExecutionVisitor visitor){
        if(!visitor.visit(this)) return false;

        for (ExecContext execEntry : execEntries) {
            if(!execEntry.visit(visitor)) return false;
        }

        return true;
    }
}
