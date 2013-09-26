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

import cap4j.session.Result;
import cap4j.task.TaskResult;

import java.util.ArrayList;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DependencyResult extends TaskResult {
    String name;
    List<String> messages;

    public DependencyResult(String name) {
        super(Result.OK);
        this.name = name;
    }

    public DependencyResult(Result result) {
        super(result);
    }

    public void add(String message) {
        initMessages();

        if(name != null){
            message = "[" + name + "]: ";
        }

        messages.add(message);
    }

    private void initMessages() {
        if(messages == null) messages = new ArrayList<String>();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(messages.size() * 64);

        for (String message : messages) {
            sb.append(message).append("\n");
        }

        return sb.append("\n").append(messages.size()).append(" errors found").toString();
    }

    public void join(DependencyResult other) {
        initMessages();
        if(other.messages != null){
            messages.addAll(other.messages);
        }

        updateResult();
    }

    public DependencyResult updateResult(){
        result = (messages == null || messages.isEmpty()) ? Result.OK : Result.ERROR;
        return this;
    }

    public static final DependencyResult OK = new DependencyResult(Result.OK);
}
