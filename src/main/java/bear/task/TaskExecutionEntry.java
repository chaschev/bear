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

import bear.session.Result;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class TaskExecutionEntry extends ExecutionEntry {
    protected Task<? extends TaskDef> task;

    public TaskExecutionEntry(ExecutionEntry parent, Task<? extends TaskDef> task) {
        super(parent);
        this.task = task;
    }

    @Override
    public Result getResult() {
        return task.getExecutionContext().taskResult.result;
    }
}
