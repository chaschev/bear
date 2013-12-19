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

import bear.core.except.ValidationException;
import bear.session.Result;
import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TaskResult {
    protected Result result;
    public transient Optional<? extends Throwable> exception;

    public TaskResult(Result result) {
        this.result = result;
        exception = Optional.absent();
    }

    public TaskResult(TaskResult other) {
        this.result = other.result;
        this.exception = other.exception;
    }

    public TaskResult(Throwable e) {
        this.result = Result.ERROR;
        exception = Optional.of(e);
    }

    public static final TaskResult OK = new TaskResult(Result.OK);

    public TaskResult and(TaskResult... results){
        return Tasks.and(Lists.asList(this, results));
    }

    public boolean ok() {
        return result.ok();
    }

    public boolean nok() {
        return result.nok();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TaskResult{");
        sb.append("result=").append(result);
        sb.append('}');
        return sb.toString();
    }

    public TaskResult throwIfError() {
        if (!ok()) {
            if (exception.isPresent()) {
                throw Exceptions.runtime(exception.get());
            }

            throw new RuntimeException(toString());
        }

        return this;
    }

    public TaskResult throwIfValidationError() {
        if(exception.isPresent() && exception.get() instanceof ValidationException){
            throw (ValidationException) exception.get();
        }

        return this;
    }

    public static TaskResult of(boolean b, String errorMessage) {
        return b ? OK : new TaskResult(new Exception(errorMessage));
    }

    public static TaskResult error(String errorMessage) {
        return of(false, errorMessage);
    }

    public Result getResult() {
        return result;
    }

    public TaskResult setResult(Result result) {
        this.result = result;
        return this;
    }

    public TaskResult setException(Exception e) {
        this.result = Result.ERROR;
        this.exception = Optional.of(e);
        return this;
    }

    public boolean isValidationError() {
        return exception.isPresent() && (exception.get() instanceof ValidationException);
    }
}
