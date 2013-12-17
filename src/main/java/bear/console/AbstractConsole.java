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

import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface AbstractConsole {
    public static abstract class Terminal{
        public abstract void print(String s);
        public void println(String s){
            print(s + "\n");
        }

        public abstract void finishWithResult(ConsoleCallbackResult callbackResult);

        public abstract boolean isDone();
    }

    /**
     */
    <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command);
}
