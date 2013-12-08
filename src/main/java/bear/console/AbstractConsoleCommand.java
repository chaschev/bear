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

package bear.console;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class AbstractConsoleCommand<T extends CommandLineResult>{
    protected long timeoutMs;
    protected StringBuilder output = new StringBuilder(8192);

    public final String id = SessionContext.randomId();

    protected TextListener textListener;
    protected ConsoleCallback callback;

    public interface TextListener{
        void on(CharSequence newText, StringBuilder wholeText);
    }

    public String asText(){
        return asText(true);
    }

    public abstract String asText(boolean forExecution);

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public AbstractConsoleCommand<T> append(CharSequence s) {
        output.append(s);

        if(textListener != null){
            textListener.on(s, output);
        }

        return this;
    }

    public StringBuilder getOutput() {
        return output;
    }

    public AbstractConsoleCommand<T> setCallback(ConsoleCallback callback) {
        this.callback = callback;
        return this;
    }

    public ConsoleCallback getCallback() {
        return callback;
    }
}
