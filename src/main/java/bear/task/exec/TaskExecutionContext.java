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

package bear.task.exec;

import bear.console.AbstractConsoleCommand;
import bear.core.SessionContext;
import bear.context.HavingContext;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.vcs.CommandLineResult;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static chaschev.lang.LangUtils.elvis;

/**
 * This is a field in Task.
 *
* @author Andrey Chaschev chaschev@gmail.com
*/
public class TaskExecutionContext extends HavingContext<TaskExecutionContext, SessionContext> {

    List<ExecutionEntry> executionEntries = new ArrayList<ExecutionEntry>();
    ExecutionEntry pendingEntry;
    public TaskResult taskResult;

    public TaskExecutionContext(SessionContext $) {
        super($);
    }

    public void onNewSubTask(Task<? extends TaskDef> task){
        pendingEntry = new TaskExecutionEntry(getLastEntry(), task);
    }

    public void onEndSubTask(Task<? extends TaskDef> task, TaskResult result) {
        pendingEntry.onEnd(result);
        executionEntries.add(pendingEntry);
        pendingEntry = null;
    }

    public <T extends CommandLineResult> void onNewCommand(AbstractConsoleCommand<T> command) {
        CommandExecutionEntry commandEntry = new CommandExecutionEntry(getLastEntry(), command);

        pendingEntry = commandEntry;

        $.getExecutionContext().currentCommand.defaultTo(commandEntry);
    }

    public <T extends CommandLineResult> void onEndCommand(AbstractConsoleCommand<T> command, T result) {
        pendingEntry.onEnd(result);
        executionEntries.add(pendingEntry);
        pendingEntry = null;
    }

    @Nullable
    public ExecutionEntry getFirstEntry(){
        if(executionEntries.isEmpty()){
            return pendingEntry;
        }

        return elvis(pendingEntry, executionEntries.get(0));
    }

    public ExecutionEntry getLastEntry(){
        if(pendingEntry != null){
            return pendingEntry;
        }

        if(executionEntries.isEmpty()) return null;

        return executionEntries.get(executionEntries.size() - 1);
    }

    public boolean isEmpty(){
        return getFirstEntry() == null;
    }

    public ExecutionEntry getPendingEntry() {
        return pendingEntry;
    }

    public DateTime getStartedAt(){
        ExecutionEntry e = getFirstEntry();

        return e == null ? null : e.getStartedAt();
    }

    public long getDuration(){
        ExecutionEntry firstEntry = getFirstEntry();

        if(firstEntry == null){
            return 0;
        }

        ExecutionEntry lastEntry = getLastEntry();

        DateTime finishedAt = lastEntry == null ? null : lastEntry.getFinishedAt();

        finishedAt = finishedAt == null ? new DateTime() : finishedAt;

        return finishedAt.getMillis() - firstEntry.getFinishedAt().getMillis();
    }

    public boolean isRunning(){
        return taskResult == null;
    }

    public boolean isFinished(){
        return !isRunning();
    }
}
