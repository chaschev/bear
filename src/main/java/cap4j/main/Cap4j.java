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

package cap4j.main;

import cap4j.core.*;
import cap4j.session.Question;
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

import static cap4j.main.Cap4j.Options.*;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Cap4j {
    public static final Logger logger = LoggerFactory.getLogger(Cap4j.class);

    @SuppressWarnings("unchecked")
    static class Options extends JOptOptions {
        public final static OptionSpec<String> HOST = parser.accepts("host", "set the database host").withRequiredArg().ofType(String.class).describedAs("host").defaultsTo("localhost");
        public final static OptionSpec<String> CAPIFY = parser.accepts("capify", "adds cap4j files to the current dir").withOptionalArg().ofType(String.class).describedAs("dir").defaultsTo(".");
        public final static OptionSpec<String> SETTINGS_FILE = parser.accepts("settings", "path to CapSettings.java").withRequiredArg().ofType(String.class).describedAs("path").defaultsTo(".cap/CapSettings.java");
        public final static OptionSpec<String> SCRIPTS_DIR = parser.accepts("scriptsDir", "path to scripts dir").withRequiredArg().ofType(String.class).describedAs("path").defaultsTo(".cap");
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

        if (options.has(CAPIFY)) {
            final File capDir = new File(options.get(CAPIFY), options.get(SCRIPTS_DIR));
//            System.out.printf("saving to dir %s%n", capDir.getAbsolutePath());;
            if (!capDir.exists()) {
                capDir.mkdirs();
            }
            copyResource("CreateNewScript.java", capDir);
            copyResource("CapSettings.java", capDir);
            copyResource("settings.properties.rename", "settings.properties", capDir);
            return;
        }


        final File settingsFile = new File(options.get(SETTINGS_FILE));
        final File scriptsDir = new File(options.get(SCRIPTS_DIR));

        Preconditions.checkArgument(settingsFile.exists(), settingsFile.getAbsolutePath() + " does not exist. Use --capify to create it.");
        Preconditions.checkArgument(scriptsDir.exists(), scriptsDir.getAbsolutePath() + " does not exist. Use --capify to create it.");

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

        System.out.printf("configuring with CapSettings.java...%n");
        final URLClassLoader classLoader = new URLClassLoader(new URL[]{buildDir.toURI().toURL()});

        final Class<?> settingsClass = classLoader.loadClass("CapSettings");
        ICapSettings c = (ICapSettings) settingsClass.newInstance();

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
        final Cap cap = global.cap;

        if (options.has(SCRIPT)) {
            logger.info("script is set in the command line to {}", options.get(SCRIPT));
            cap.deployScript.defaultTo(options.get(SCRIPT));
        } else {
            new Question("Enter a script name to run:",
                transform(loadedScriptClasses, new Function<Class<?>, String>() {
                    public String apply(Class<?> input) {
                        return input.getSimpleName();
                    }
                }),
                cap.deployScript).ask();
        }

        final Optional<Class<?>> scriptToRun = Iterables.tryFind(loadedScriptClasses, new Predicate<Class<?>>() {
            @Override
            public boolean apply(Class<?> input) {
                return input.getName().equals(global.var(cap.deployScript));
            }
        });

        if (scriptToRun.isPresent()) {
            System.out.printf("running script %s...%n", scriptToRun.get().getSimpleName());

            final Script script = (Script) scriptToRun.get().newInstance();

            script.setProperties(global, scriptsDir);

            new Cap4jRunner(c, script).run();
        } else {
            System.err.printf("Didn't find script with name %s. Exiting.%n", global.var(cap.deployScript));
            System.exit(-1);
        }
//        System.out.println(localCtx.var(CapConstants.stages).getStages());
    }

    private static void copyResource(String resource, File capDir) throws IOException {
        copyResource(resource, resource, capDir);
    }

    private static void copyResource(String resource, String destName, File capDir) throws IOException {
        final File file = new File(capDir, destName);
        System.out.printf("creating %s%n", file.getAbsolutePath());

        IOUtils.copy(Cap4j.class.getResourceAsStream("/" + resource), new FileOutputStream(file));
    }

    public static class Cap4jRunner {
        private ICapSettings capSettings;
        private GlobalContextFactory factory;
        private Script script;

        public Cap4jRunner(ICapSettings capSettings, Script script) {
            this.capSettings = capSettings;
            this.factory = GlobalContextFactory.INSTANCE;
            this.script = script;
        }

        public void run() throws Exception {
            capSettings.configure(factory);
            script
                .setProperties(capSettings.getGlobal(), null)
                .run();
        }
    }
}
