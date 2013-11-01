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

import bear.session.Result;
import bear.task.TaskResult;
import org.joda.time.DateTime;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class ExecutionEntry {
    public final ExecutionEntry parent;

    DateTime startedAt = new DateTime();
    DateTime finishedAt;

    protected ExecutionEntry(ExecutionEntry parent) {
        this.parent = parent;
    }

    public void onEnd(TaskResult result) {
        finishedAt = new DateTime();
    }

    public DateTime getStartedAt() {
        return startedAt;
    }

    public DateTime getFinishedAt() {
        return finishedAt;
    }

    public abstract Result getResult() ;
}
