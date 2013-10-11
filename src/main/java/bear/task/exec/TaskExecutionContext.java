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
import bear.plugins.HavingContext;
import bear.task.Task;
import bear.task.TaskResult;
import bear.vcs.CommandLineResult;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static chaschev.lang.LangUtils.elvis;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class TaskExecutionContext extends HavingContext<TaskExecutionContext> {

    List<ExecutionEntry> executionEntries = new ArrayList<ExecutionEntry>();
    ExecutionEntry pendingEntry;
    public TaskResult taskResult;


    public TaskExecutionContext(SessionContext $) {
        super($);
    }

    public void onNewSubTask(Task task){
        pendingEntry = new TaskExecutionEntry(task);
    }

    public void onEndSubTask(Task task, TaskResult result) {
        pendingEntry.onEnd(result);
        executionEntries.add(pendingEntry);
        pendingEntry = null;
    }

    public <T extends CommandLineResult> void onNewCommand(AbstractConsoleCommand<T> command) {
        CommandExecutionEntry commandEntry = new CommandExecutionEntry(command);

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
}
