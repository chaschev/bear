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
//    TaskExecutionEntry selfEntry;
    List<ExecContext> execEntries = new ArrayList<ExecContext>();
    public TaskResult taskResult;
    protected Task<? extends TaskDef> task;


    public TaskExecutionContext(SessionContext $, Task<? extends TaskDef> task) {
        super($, getParentContext(task));
        this.task = task;

//        selfEntry = new TaskExecutionEntry(task.getParentEntry(), task);
    }

    private static TaskExecutionContext getParentContext(@Nonnull Task<? extends TaskDef> task) {
        Task<TaskDef> parent = task.getParent();

        if (parent == null) return null;

        return parent.getExecutionContext();
    }

    protected ExecContext findEntryByTask(Task<? extends TaskDef> task) {
//        for (ExecutionEntry e : executionEntries) {
//            if (e instanceof TaskExecutionEntry) {
//                if (((TaskExecutionEntry) e).task == task) return e;
//            }
//        }

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

    public void addNewSubTask(Task<? extends TaskDef> subTask) {
//        executionEntries.add(new TaskExecutionEntry(subTask.getParentEntry(), subTask));
        execEntries.add(new TaskExecutionContext($, subTask));
    }

    public void onEndSubTask(Task<? extends TaskDef> task, TaskResult result) {
        findEntryByTask(task).onEnd(result);
    }

    public <T extends CommandLineResult> void addNewCommand(AbstractConsoleCommand<T> command) {
        CommandContext co = new CommandContext($, this, command);

        execEntries.add(co);

        $.getExecutionContext().currentCommand.defaultTo(co);
    }

    public <T extends CommandLineResult> void onEndCommand(AbstractConsoleCommand<T> command, T result) {
        findEntryByCommand(command).onEnd(result);
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

    public DateTime getStartedAt() {
        Optional<ExecContext> e = getFirstEntry();

        if (e.isPresent()) return e.get().getStartedAt();

        throw new IllegalStateException("not started");
    }

    public long getDuration() {
        Optional<ExecContext> firstEntry = getFirstEntry();

        if (!firstEntry.isPresent()) {
            return 0;
        }

        Optional<ExecContext> lastEntry = getLastEntry();

        DateTime finishedAt = lastEntry.isPresent() ? null : lastEntry.get().getFinishedAt();

        finishedAt = finishedAt == null ? new DateTime() : finishedAt;

        return finishedAt.getMillis() - firstEntry.get().getFinishedAt().getMillis();
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

    public Optional<TaskResult> lastResult(){
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

    public Optional<TaskResult> findResult(final TaskDef<Task> def){
        final TaskResult[] r = new TaskResult[1];

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
