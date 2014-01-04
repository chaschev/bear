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

package bear.session;

import bear.task.TaskResult;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public enum Result {
    OK, CONNECTION_ERROR, TIMEOUT, ERROR;

    public static Result and(Result... results) {
        for (Result result : results) {
            if (result != OK) {
                return result;
            }
        }

        return OK;
    }

    public boolean ok() {
        return this == OK;
    }

    public boolean nok() {
        return this != OK;
    }

    public TaskResult<?> toTaskResult(){
        if(this == OK) return TaskResult.OK;

        return new TaskResult(this);
    }
}
