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

package bear.plugins.sh;

import bear.core.SessionContext;
import bear.plugins.CommandInterpreter;
import bear.plugins.Plugin;
import bear.plugins.PluginShellMode;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ShShellMode extends PluginShellMode implements CommandInterpreter {
    public ShShellMode(Plugin plugin, String shortCut) {
        super(plugin, shortCut);
    }

    public Task interpret(final String command, SessionContext $, Task parent, TaskDef taskDef){
        return new Task<TaskDef>(parent, taskDef, $) {
            @Override
            protected TaskResult exec(TaskRunner runner) {
                return $.sys.script()
                    .timeoutSec(60)
                    .line().addRaw(command).build()
                    .run();
            }
        };
    }

    @Override
    public boolean multiLine() {
        return true;
    }
}
