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
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    public static <T extends TaskResult> Optional<T> okOrAbsent(@Nonnull Optional<T> result){
        Preconditions.checkNotNull(result);

        if(!result.isPresent() || !result.get().ok()) return Optional.absent();

        return result;
    }

    public static <T extends TaskResult> Optional<T> okOrAbsent(@Nullable T result){
        if(result != null && result.ok()) return Optional.of(result);
        return Optional.absent();
    }

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
        if(exception.isPresent()){
            sb.append(", exception=").append(Throwables.getStackTraceAsString(exception.get()));
        }
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

    protected TaskResult throwIfException() {
//        return throwIfError();
        if(exception.isPresent() /*&& exception.get() instanceof ValidationException*/){
            throw Exceptions.runtime(exception.get());
        }

        return this;
    }

    protected TaskResult throwIfNot(Class... exceptions) {
        if(exception.isPresent()){
            for (Class<? extends Exception> aClass : exceptions) {
                if(aClass.isAssignableFrom(exception.get().getClass())){
                    return this;
                }
            }

            throw Exceptions.runtime(exception.get());
        }

        return this;
    }

    protected TaskResult throwIfExceptionIs(Class<? extends Exception>... exceptions) {
        if(exception.isPresent()){
            for (Class<? extends Exception> aClass : exceptions) {
                if(aClass.isAssignableFrom(exception.get().getClass())){
                    throw Exceptions.runtime(exception.get());
                }
            }
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

    public TaskResult setError() {
        return setResult(Result.ERROR);
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
