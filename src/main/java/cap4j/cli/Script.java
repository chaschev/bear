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

package cap4j.cli;

import cap4j.scm.CommandLineResult;
import cap4j.session.SystemEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
* User: chaschev
* Date: 9/24/13
*/
public class Script {
    public String cd = ".";

    protected SystemEnvironment system;

    public List<CommandLine> lines = new ArrayList<CommandLine>();

    public Script(SystemEnvironment system) {
        this.system = system;
    }

    public CommandLine line(){
        final CommandLine line = system.line();

        lines.add(line);

        return line;
    }

    public Script add(CommandLine commandLine) {
        lines.add(commandLine);

        return this;
    }

    public Script cd(String cd) {
        this.cd = cd;
        return this;
    }


    public CommandLineResult run() {
        return system.run(this);
    }
}
