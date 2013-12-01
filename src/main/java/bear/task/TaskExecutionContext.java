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
import bear.context.HavingContext;
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


public class TaskExecutionContext extends HavingContext<TaskExecutionContext, SessionContext> {
    TaskExecutionEntry selfEntry;
    List<ExecutionEntry> executionEntries = new ArrayList<ExecutionEntry>();
    public TaskResult taskResult;

    public TaskExecutionContext(SessionContext $, Task<? extends TaskDef> task) {
        super($);

        selfEntry = new TaskExecutionEntry(task.getParentEntry(), task);
    }

    protected ExecutionEntry findEntryByTask(Task<? extends TaskDef> task) {
        for (ExecutionEntry e : executionEntries) {
            if (e instanceof TaskExecutionEntry) {
                if (((TaskExecutionEntry) e).task == task) return e;
            }
        }

        return null;
    }

    protected ExecutionEntry findEntryByCommand(AbstractConsoleCommand command) {
        for (ExecutionEntry e : executionEntries) {
            if (e instanceof CommandExecutionEntry) {
                if (((CommandExecutionEntry) e).command == command) return e;
            }
        }

        return null;
    }

    public void addNewSubTask(Task<? extends TaskDef> subTask) {
        //
        executionEntries.add(new TaskExecutionEntry(subTask.getParentEntry(), subTask));
    }

    public void onEndSubTask(Task<? extends TaskDef> task, TaskResult result) {
        findEntryByTask(task).onEnd(result);
    }

    public <T extends CommandLineResult> void addNewCommand(AbstractConsoleCommand<T> command) {
        CommandExecutionEntry commandEntry = new CommandExecutionEntry(selfEntry, command);

        executionEntries.add(commandEntry);

        $.getExecutionContext().currentCommand.defaultTo(commandEntry);
    }

    public <T extends CommandLineResult> void onEndCommand(AbstractConsoleCommand<T> command, T result) {
        findEntryByCommand(command).onEnd(result);
    }

    @Nonnull
    public Optional<ExecutionEntry> getFirstEntry() {
        if (executionEntries.isEmpty()) {
            return absent();
        }

        return of(executionEntries.get(0));
    }

    @Nonnull
    public Optional<ExecutionEntry> getLastEntry() {
        if (executionEntries.isEmpty()) return absent();

        return of(executionEntries.get(executionEntries.size() - 1));
    }

    public boolean isEmpty() {
        return getFirstEntry() == null;
    }

    public DateTime getStartedAt() {
        Optional<ExecutionEntry> e = getFirstEntry();

        if (e.isPresent()) return e.get().getStartedAt();

        throw new IllegalStateException("not started");
    }

    public long getDuration() {
        Optional<ExecutionEntry> firstEntry = getFirstEntry();

        if (!firstEntry.isPresent()) {
            return 0;
        }

        Optional<ExecutionEntry> lastEntry = getLastEntry();

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

    public TaskExecutionEntry getExecutionEntry() {
        return selfEntry;
    }
}
