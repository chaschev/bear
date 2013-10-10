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

package bear.main;

import bear.core.GlobalContext;
import chaschev.util.JOptOptions;
import com.google.common.base.Optional;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearMain {
    public static final Logger logger = LoggerFactory.getLogger(BearMain.class);

    @SuppressWarnings("unchecked")
    static class Options extends JOptOptions {
        public final static OptionSpec<String> BEARIFY = parser.accepts("bearify", "adds bear files to the current dir").withOptionalArg().ofType(String.class).describedAs("dir").defaultsTo(".");
        public final static OptionSpec<String> SETTINGS_FILE = parser.accepts("settings", "path to BearSettings.java").withRequiredArg().ofType(String.class).describedAs("path").defaultsTo(".bear/BearSettings.java");
        public final static OptionSpec<String> SCRIPTS_DIR = parser.accepts("scriptsDir", "path to scripts dir").withRequiredArg().ofType(String.class).describedAs("path").defaultsTo(".bear");
        public final static OptionSpec<String> SCRIPT = parser.accepts("script", "script to run").withRequiredArg().ofType(String.class).describedAs("path");

        public final static OptionSpec<Void> HELP = parser.acceptsAll(newArrayList("h", "help"), "show help");

        public Options(String[] args) {
            super(args);
        }
    }

    public static void main(String[] args) throws Exception {
        BearCommandLineConfigurator configurator = new BearCommandLineConfigurator(args).configure();

        if (configurator.shouldExit()) {
            return;
        }

        Optional<CompiledEntry> scriptToRun = configurator.getScriptToRun();
        GlobalContext global = configurator.getGlobal();
        bear.core.Bear bear = configurator.getBear();

        if (scriptToRun.isPresent()) {
            System.out.printf("running script %s...%n", scriptToRun.get().getName());

            new BearRunner(configurator).run();
        } else {
            System.err.printf("Didn't find script with name %s. Exiting.%n", global.var(bear.deployScript));
            System.exit(-1);
        }
    }

}
