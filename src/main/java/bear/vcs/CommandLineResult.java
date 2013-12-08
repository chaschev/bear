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

import bear.core.Bear;
import bear.core.SessionContext;
import bear.session.Result;
import bear.session.Variables;
import bear.task.TaskResult;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class CommandLineResult extends TaskResult{
    public transient String script;
    public transient String text;
    public int exitCode;
    public Object value;

    CommandLineResult() {
        super(Result.OK);
    }

    public CommandLineResult(String script, String text) {
        super(Result.OK);
        this.script = cut(script);
        this.text = text;
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

    public CommandLineResult(String script, String text, Result result) {
        super(result);
        this.script = cut(script);
        this.text = text;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CommandLineResult{");
        sb.append("result='").append(result).append('\'');
        if(exception.isPresent()) sb.append(", exception='").append(exception.get().toString()).append('\'');
        sb.append(", script='").append(script).append('\'');
        sb.append(", text='").append(text).append('\'');
        sb.append(", exitCode=").append(exitCode);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public CommandLineResult throwIfError() {
        super.throwIfError();
        return this;
    }

    public CommandLineResult setException(Throwable e) {
        result = Result.ERROR;
        exception = Optional.of(e);

        return this;
    }

    public CommandLineResult validate(SessionContext $){
        try {
            if(result.ok() && text != null){
                $.var($.bear.pathValidator).apply(text);
            }
        } catch (Bear.ValidationException e) {
            setException(e);
        }

        return this;
    }

    public static final CommandLineResult OK = new CommandLineResult("default", "OK");
}
