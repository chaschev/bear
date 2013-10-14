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
import bear.task.TaskResult;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class CommandLineResult extends TaskResult{
    public transient String text;
    public int exitCode;

    public CommandLineResult(String text) {
        super(null);
        this.text = text;
    }

    public CommandLineResult(Result result, String text) {
        this(text, result);
    }

    public CommandLineResult(String text, Result result) {
        super(result);
        this.text = text;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CommandLineResult{");
        sb.append("result=").append(result);
        sb.append(", text='").append(text).append('\'');
        sb.append(", exitCode=").append(exitCode);
        sb.append('}');
        return sb.toString();
    }
}
