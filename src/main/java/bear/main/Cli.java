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

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.GlobalContextFactory;
import bear.plugins.DependencyInjection;
import bear.plugins.HavingContext;
import bear.session.DynamicVariable;
import chaschev.util.JOptOptions;
import com.google.common.base.Preconditions;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;
import joptsimple.util.KeyValuePair;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import static bear.main.Cli.Options.HELP;
import static bear.main.Cli.Options.VARIABLES;
import static bear.session.Variables.*;
import static com.google.common.collect.Lists.newArrayList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Cli extends HavingContext<Cli, GlobalContext> {
    public static final Logger logger = LoggerFactory.getLogger(Cli.class);

    public final DynamicVariable<File>
        bearDir = newVar(new File(".bear")),
        settingsFile = convert(concat(bearDir, "/BearSettings.java"), TO_FILE),
        scriptsDir = equalTo(bearDir),
        propertiesFile = convert(concat(bearDir, "/settings.properties"), TO_FILE),
        script = undefined(),
        buildDir = convert(concat(scriptsDir, "/classes"), TO_FILE)
            ;

    public final DynamicVariable<Boolean>
        bearify = newVar(false);

    public final DynamicVariable<Properties>
        newRunProperties = newVar(new Properties());

    private boolean shouldExit;
    private String[] args;
    protected final GlobalContext global;

    protected Bear bear;
    protected GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    protected BearFX bearFX;

    public Cli(String... args) {
        super(GlobalContextFactory.INSTANCE.getGlobal());

        this.args = args;

        this.global = $;

        bear = $.bear;

        DependencyInjection.nameVars(this, $);
    }

    private static void copyResource(String resource, File bearDir) throws IOException {
        copyResource(resource, resource, bearDir);
    }

    private static void copyResource(String resource, String destName, File bearDir) throws IOException {
        final File file = new File(bearDir, destName);
        System.out.printf("creating %s%n", file.getAbsolutePath());

        IOUtils.copy(BearMain.class.getResourceAsStream("/" + resource), new FileOutputStream(file));
    }

    boolean shouldExit() {
        return shouldExit;
    }

    public GlobalContext getGlobal() {
        return $;
    }

    public Bear getBear() {
        return bear;
    }

    //todo move to vars framework
    public Cli configure() throws IOException {

        Options options = new Options(args);

        if (options.has(HELP)) {
            System.out.println(options.printHelpOn());
            shouldExit = true;
            return this;
        }

        Map<String, String> env = System.getenv();

        for (Map.Entry<String, String> entry : env.entrySet()) {
            $.convertAndPutConst(entry.getKey(), entry.getValue(),
                $.variableRegistry.getType(entry.getKey()));
        }

        for (KeyValuePair pair : options.getList(VARIABLES)) {
            //todo move registry into global AbstractContext
            $.convertAndPutConst(pair.key, pair.value, $.variableRegistry.getType(pair.key));
        }

        $.loadProperties($(propertiesFile));

        if ($(bearify)) {
            final File dir = $(bearDir);

//            System.out.printf("saving to dir %s%n", bearDir.getAbsolutePath());;

            if (!dir.exists()) {
                dir.mkdirs();
            }

            copyResource("CreateNewScript.java", dir);
            copyResource("BearSettings.java", dir);
            copyResource("settings.properties.rename", "settings.properties", dir);

            shouldExit = true;

            return this;
        }

        fileRequired($(scriptsDir));

        return this;
    }


    private static File fileRequired(File settingsFile) {
        Preconditions.checkArgument(settingsFile.exists(), settingsFile.getAbsolutePath() + " does not exist. Use --bearify to create it.");
        return settingsFile;
    }


    public GlobalContextFactory getFactory() {
        return factory;
    }

    @SuppressWarnings("unchecked")
    static class Options extends JOptOptions {
        public final static OptionSpec<KeyValuePair> VARIABLES =
            parser.acceptsAll(Arrays.asList("V", "vars"), "set global vars").withRequiredArg()
                .withValuesSeparatedBy(",")
                .withValuesConvertedBy(new KeyValueConverter())
                .ofType(KeyValuePair.class).describedAs("var list");

        public final static OptionSpec<Void> HELP = parser.acceptsAll(newArrayList("h", "help"), "show help");

        public Options(String[] args) {
            super(args);
        }
    }

    public static class KeyValueConverter implements ValueConverter<KeyValuePair> {
        @Override
        public KeyValuePair convert(String value) {
            return KeyValuePair.valueOf(value);
        }

        @Override
        public Class<KeyValuePair> valueType() {
            return KeyValuePair.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }

}
