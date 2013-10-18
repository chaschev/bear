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

package bear.core;

import bear.task.Task;
import bear.task.TaskDef;
import chaschev.lang.OpenBean;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public enum Registry {
    INSTANCE;

    public Task<TaskDef> getByName(String name) {
        String className;
        String taskName;

        if (!name.contains(".")) {
            className = "Bear";
        } else {
            className = StringUtils.substringBefore(name, ".");
        }

        taskName = StringUtils.substringAfter(name, ".");

        return getTask(className, taskName);
    }

    private Task<TaskDef> getTask(String className, String taskName) {
        if (!className.equals("Bear")) {
            throw new UnsupportedOperationException("todo");
        }

        return (Task<TaskDef>) OpenBean.getFieldValue(Bear.class, taskName);
    }
}
