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

package bear.vcs;

import bear.session.Result;
import bear.session.Variables;
import bear.task.TaskResult;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class CommandLineResult<SELF extends CommandLineResult> extends TaskResult<SELF> {
    public transient String script;
    public transient String output;
    public int exitCode;
    public Object value;

    protected CommandLineResult() {
        super(Result.OK);
    }

    public CommandLineResult(CommandLineResult<?> other) {
        super(other.result);
        this.script = other.script;
        this.output = other.output;
        this.exitCode = other.exitCode;
        this.value = other.value;
    }

    public CommandLineResult(String script, String commandOutput) {
        super(Result.OK);
        this.script = cut(script);
        this.output = commandOutput;
    }

    private static String cut(String script) {
        if(script == null) return null;

        try {
            String next = Variables.LINE_SPLITTER.split(script).iterator().next();

            return StringUtils.substring(next, 0, 80);
        } catch (Exception e) {
            return script.trim();
        }
    }

    public CommandLineResult(String script, String output, Result result) {
        super(result);
        this.script = cut(script);
        this.output = output;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CommandLineResult{");
        sb.append("result='").append(result).append('\'');
        if(exception.isPresent()) sb.append(", exception='").append(exception.get().toString()).append('\'');
        sb.append(", script='").append(script).append('\'');
        sb.append(", text='").append(output).append('\'');
        sb.append(", exitCode=").append(exitCode);
        sb.append('}');
        return sb.toString();
    }

    public CommandLineResult<?> setException(Throwable e) {
        result = Result.ERROR;
        exception = Optional.of(e);

        return this;
    }

    public static final CommandLineResult<?> OK = new CommandLineResult<CommandLineResult>("default", "OK");

    public CommandLineResult<?> copyFrom(CommandLineResult<?> result) {
        if(result.exception.isPresent()){
            exception = result.exception;
        }

        this.result = result.result;
        this.script = result.script;
        this.output = result.output;
        this.exitCode = result.exitCode;
        this.value = result.value;

        return this;
    }

    public static CommandLineResult<?> error(Exception e){
        CommandLineResult<?> r = new CommandLineResult();

        r.setException(e);

        return r;
    }
}
