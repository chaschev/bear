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
import bear.session.Result;
import bear.task.TaskResult;
import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CommandExecutionEntry extends ExecutionEntry {
    AbstractConsoleCommand command;

    protected CommandLineResult result;

    public <T extends CommandLineResult> CommandExecutionEntry(AbstractConsoleCommand<T> command) {
        this.command = command;
    }

    @Override
    public void onEnd(TaskResult result) {
        super.onEnd(result);

        this.result = (CommandLineResult) result;
    }

    @Override
    public Result getResult() {
        return result.result;
    }

    @Override
    public String toString() {
        return command.asText(false);
    }
}
