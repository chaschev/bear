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

package cap4j.core;

import cap4j.task.Task;
import com.chaschev.chutils.util.OpenBean2;
import org.apache.commons.lang3.StringUtils;

/**
 * User: achaschev
 * Date: 8/12/13
 * Time: 1:04 AM
 */
public enum Registry {
    INSTANCE;

    public Task getByName(String name){
        String className;
        String taskName;

        if(!name.contains(".")){
            className = "CapConstants";
        }else {
            className = StringUtils.substringBefore(name, ".");
        }

        taskName = StringUtils.substringAfter(name, ".");

        return getTask(className, taskName);
    }

    private Task getTask(String className, String taskName) {
        if(!className.equals("CapConstants")){
            throw new UnsupportedOperationException("todo");
        }

        return (Task) OpenBean2.getFieldValue2(Cap.class, taskName);
    }
}
