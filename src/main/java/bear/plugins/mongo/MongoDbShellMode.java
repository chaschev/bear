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

package bear.plugins.mongo;

import bear.core.SessionContext;
import bear.plugins.PluginShellMode;
import bear.task.Task;
import bear.task.TaskDef;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class MongoDbShellMode extends PluginShellMode<MongoDbPlugin> {
    public MongoDbShellMode(MongoDbPlugin plugin) {
        super(plugin, plugin.cmdAnnotation());
    }

    @Override
    public Task<?> interpret(String command, SessionContext $, Task parent, TaskDef taskDef) {
        return plugin.scriptTask(command, parent, taskDef, $);
    }
}
