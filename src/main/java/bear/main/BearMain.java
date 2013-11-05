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

import joptsimple.OptionParser;
import joptsimple.OptionSpec;
import joptsimple.util.KeyValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearMain {
    public static final Logger logger = LoggerFactory.getLogger(BearMain.class);



    public static void main(String[] args) throws Exception {
//        Cli configurator = new Cli(args).configure();
//
//        if (configurator.shouldExit()) {
//            return;
//        }
//
//        Optional<CompiledEntry> scriptToRun = configurator.getScriptToRun();
//        GlobalContext global = configurator.getGlobal();
//        bear.core.Bear bear = configurator.getBear();
//
//        if (scriptToRun.isPresent()) {
//            System.out.printf("running script %s...%n", scriptToRun.get().getName());
//
//            new BearRunner(configurator)
//                .shutdownAfterRun(true)
//                .prepareToRun();
//        } else {
//            System.err.printf("Didn't find a script with name %s. Exiting.%n", global.var(bear.deployScript));
//            System.exit(-1);
//        }
    }

    public static class Test{
        public static void main(String[] args) {
            OptionParser parser = new OptionParser();
            parser.allowsUnrecognizedOptions();
            OptionSpec<KeyValuePair> spec = parser.accepts("D").withRequiredArg().withValuesConvertedBy(new Cli.KeyValueConverter()).withValuesSeparatedBy(",");

            List<KeyValuePair> list = parser.parse("-Dx=y,a=b").valuesOf(spec);

            printList(list);

            List<KeyValuePair> list2 = parser.parse("-Dx=y", "-Da=b").valuesOf(spec);

            printList(list2);
        }

        private static void printList(List<KeyValuePair> list) {
            for (KeyValuePair kv : list) {
                System.out.printf("%s = %s%n", kv.key, kv.value);
            }
        }


    }

}
