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

package bear.context;

import chaschev.util.JOptOptions;
import joptsimple.OptionSpec;
import joptsimple.util.KeyValuePair;

import java.util.Arrays;

import static com.google.common.collect.Lists.newArrayList;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
@SuppressWarnings("unchecked")
public abstract class AppOptions extends JOptOptions {
    public final static OptionSpec<KeyValuePair> VARIABLES =
        parser.acceptsAll(Arrays.asList("V", "vars"), "set global variables").withRequiredArg()
            .withValuesSeparatedBy(",")
            .withValuesConvertedBy(new AppCli.KeyValueConverter())
            .ofType(KeyValuePair.class).describedAs("var list");

    public final static OptionSpec<String>
        LOG_LEVEL = parser.accepts("log-level", "set the logging level (INFO, ERROR, ...)").withRequiredArg().describedAs("level");

    public final static OptionSpec<Void>
        HELP = parser.acceptsAll(newArrayList("h", "help"), "show help"),
        VERSION = parser.acceptsAll(newArrayList("v", "version"), "print version");

    public AppOptions(String[] args) {
        super(args);
    }
}
