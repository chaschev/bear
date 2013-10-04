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
import bear.core.GlobalContextFactory;
import bear.core.IBearSettings;
import bear.session.Question;
import com.chaschev.chutils.util.Exceptions;
import com.chaschev.chutils.util.JOptOptions;
import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.PatternFilenameFilter;
import joptsimple.OptionSpec;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static bear.main.Bear.Options.*;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Bear {
    public static final Logger logger = LoggerFactory.getLogger(Bear.class);

    @SuppressWarnings("unchecked")
    static class Options extends JOptOptions {
        public final static OptionSpec<String> HOST = parser.accepts("host", "set the database host").withRequiredArg().ofType(String.class).describedAs("host").defaultsTo("localhost");
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
        final Options options = new Options(args);

        if (options.has(HELP)) {
            System.out.println(options.printHelpOn());
            return;
        }

        if (options.has(BEARIFY)) {
            final File bearDir = new File(options.get(BEARIFY), options.get(SCRIPTS_DIR));
//            System.out.printf("saving to dir %s%n", bearDir.getAbsolutePath());;
            if (!bearDir.exists()) {
                bearDir.mkdirs();
            }
            copyResource("CreateNewScript.java", bearDir);
            copyResource("CapSettings.java", bearDir);
            copyResource("settings.properties.rename", "settings.properties", bearDir);
            return;
        }


        final File settingsFile = new File(options.get(SETTINGS_FILE));
        final File scriptsDir = new File(options.get(SCRIPTS_DIR));

        Preconditions.checkArgument(settingsFile.exists(), settingsFile.getAbsolutePath() + " does not exist. Use --bearify to create it.");
        Preconditions.checkArgument(scriptsDir.exists(), scriptsDir.getAbsolutePath() + " does not exist. Use --bearify to create it.");

        final File[] files = scriptsDir.listFiles(new PatternFilenameFilter("^.*\\.java$"));

        final ArrayList<String> params = new ArrayList<String>(8 + files.length);

        final File buildDir = new File(scriptsDir, "classes");

        if (!buildDir.exists()) {
            buildDir.mkdir();
        }

        Collections.addAll(params, "-d", buildDir.getAbsolutePath());
        final List<File> filesList = Lists.asList(settingsFile, files);

        final List<String> filePaths = Lists.transform(filesList, new Function<File, String>() {
            public String apply(File input) {
                return input.getAbsolutePath();
            }
        });

        params.addAll(filePaths);

        System.out.printf("compiling %s%n", params);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        final int r = compiler.run(null, null, null, params.toArray(new String[params.size()]));

        if (r == 0) {
            System.out.printf("compilation OK.%n");
        } else {
            System.out.printf("compilation failed.%n");
        }

        System.out.printf("configuring with BearSettings.java...%n");
        final URLClassLoader classLoader = new URLClassLoader(new URL[]{buildDir.toURI().toURL()});

        final Class<?> settingsClass = classLoader.loadClass("BearSettings");
        IBearSettings c = (IBearSettings) settingsClass.newInstance();

        final GlobalContextFactory factory = GlobalContextFactory.INSTANCE;
        factory.getGlobal().loadProperties(new File(scriptsDir, "settings.properties"));

        List<Class<?>> loadedScriptClasses = Lists.newArrayList(Iterables.filter(Lists.transform(filesList, new Function<File, Class<?>>() {
            public Class<?> apply(File input) {
                try {
                    return classLoader.loadClass(FilenameUtils.getBaseName(input.getName()));
                } catch (ClassNotFoundException e) {
                    throw Exceptions.runtime(e);
                }
            }
        }), new Predicate<Class<?>>() {
            @Override
            public boolean apply(Class<?> input) {
                return Script.class.isAssignableFrom(input);
            }
        }));

        final GlobalContext global = factory.getGlobal();
        final bear.core.Bear bear = global.bear;

        if (options.has(SCRIPT)) {
            logger.info("script is set in the command line to {}", options.get(SCRIPT));
            bear.deployScript.defaultTo(options.get(SCRIPT));
        } else {
            new Question("Enter a script name to run:",
                transform(loadedScriptClasses, new Function<Class<?>, String>() {
                    public String apply(Class<?> input) {
                        return input.getSimpleName();
                    }
                }),
                bear.deployScript).ask();
        }

        final Optional<Class<?>> scriptToRun = Iterables.tryFind(loadedScriptClasses, new Predicate<Class<?>>() {
            @Override
            public boolean apply(Class<?> input) {
                return input.getName().equals(global.var(bear.deployScript));
            }
        });

        if (scriptToRun.isPresent()) {
            System.out.printf("running script %s...%n", scriptToRun.get().getSimpleName());

            final Script script = (Script) scriptToRun.get().newInstance();

            script.setProperties(global, scriptsDir);

            new BearRunner(c, script).run();
        } else {
            System.err.printf("Didn't find script with name %s. Exiting.%n", global.var(bear.deployScript));
            System.exit(-1);
        }
//        System.out.println(localCtx.var(BearConstants.stages).getStages());
    }

    private static void copyResource(String resource, File bearDir) throws IOException {
        copyResource(resource, resource, bearDir);
    }

    private static void copyResource(String resource, String destName, File bearDir) throws IOException {
        final File file = new File(bearDir, destName);
        System.out.printf("creating %s%n", file.getAbsolutePath());

        IOUtils.copy(Bear.class.getResourceAsStream("/" + resource), new FileOutputStream(file));
    }

    public static class BearRunner {
        private IBearSettings bearSettings;
        private GlobalContextFactory factory;
        private Script script;

        public BearRunner(IBearSettings bearSettings, Script script) {
            this.bearSettings = bearSettings;
            this.factory = GlobalContextFactory.INSTANCE;
            this.script = script;
        }

        public void run() throws Exception {
            bearSettings.configure(factory);
            script
                .setProperties(bearSettings.getGlobal(), null)
                .run();
        }
    }
}
