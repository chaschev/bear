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
import com.google.common.base.Optional;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DependencyResult extends TaskResult {
    public String name;

    @Nonnull
    public Optional<ArrayList<String>> messages = Optional.absent();

    public DependencyResult(String name) {
        super(Result.OK);
        this.name = name;
    }

    public DependencyResult(Result result) {
        super(result);
    }

    public DependencyResult add(String message) {
        if(name != null){
            message = "[" + name + "]: " + message;
        }

        createIfNotPresent();

        messages.get().add(message);

        result = Result.ERROR;

        return this;
    }

    private void createIfNotPresent() {
        if(!messages.isPresent()) messages = Optional.of(new ArrayList<String>());
    }

    @Override
    public String toString() {
        if(!messages.isPresent()){
            return "0 errors";
        }

        final StringBuilder sb = new StringBuilder(messages.get().size() * 64);

        for (String message : messages.get()) {
            sb.append(message).append("\n");
        }

        return sb.append("\n").append(messages.get().size()).append(" errors found").toString();
    }

    public void join(DependencyResult other) {
        createIfNotPresent();

        if(other.messages.isPresent()){
            messages.get().addAll(other.messages.get());
        }

        updateResult();
    }

    public DependencyResult updateResult(){
        result = messages.isPresent() && !messages.get().isEmpty()? Result.ERROR : Result.OK;

        if(result.nok() && !exception.isPresent()){
            exception = Optional.of(new DependencyException(this));
        }

        return this;
    }

    public static final DependencyResult OK = new DependencyResult(Result.OK);
}
