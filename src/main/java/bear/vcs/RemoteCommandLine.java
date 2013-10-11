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

import bear.cli.CommandLine;
import bear.cli.Script;
import bear.session.SystemEnvironment;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class RemoteCommandLine<T extends CommandLineResult> extends CommandLine<T> {

    public RemoteCommandLine(SystemEnvironment sys) {
        super(sys);
    }

    public RemoteCommandLine(Script script) {
        super(script);
    }
}