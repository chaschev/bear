package cap4j.main;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.core.Script;
import cap4j.session.Question;
import com.chaschev.chutils.util.Exceptions;
import com.chaschev.chutils.util.JOptOptions;
import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.PatternFilenameFilter;
import joptsimple.OptionSpec;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.Nullable;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static cap4j.core.CapConstants.deployScript;
import static cap4j.core.GlobalContext.localCtx;
import static cap4j.main.Cap4j.Options.*;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

/**
 * User: achaschev
 * Date: 8/5/13
 */
public class Cap4j {
    @SuppressWarnings("unchecked")
    static class Options extends JOptOptions {
        public final static OptionSpec<String> HOST = parser.accepts("host", "set the database host").withRequiredArg().ofType(String.class).describedAs("host").defaultsTo("localhost");
        public final static OptionSpec<String> CAPIFY = parser.accepts("capify", "adds cap4j files to the current dir").withOptionalArg().ofType(String.class).describedAs("dir").defaultsTo(".");
        public final static OptionSpec<String> SETTINGS_FILE = parser.accepts("settings", "path to Settings.java").withRequiredArg().ofType(String.class).describedAs("path").defaultsTo(".cap/Settings.java");
        public final static OptionSpec<String> SCRIPTS_DIR = parser.accepts("scriptsDir", "path to scripts dir").withRequiredArg().ofType(String.class).describedAs("path").defaultsTo(".cap");

        public final static OptionSpec<Void> HELP = parser.acceptsAll(newArrayList("h", "help"), "show help");

        public Options(String[] args) {
            super(args);
        }
    }

    public static void main(String[] args) throws Exception {
        final Options options = new Options(args);

        if(options.has(HELP)){
            options.printHelpOn(System.out);
            return;
        }

        if(options.has(CAPIFY)){
            throw new UnsupportedOperationException("Not implemented yet. Copy the files, please.");
        }

        final File settingsFile = new File(options.get(SETTINGS_FILE));
        final File scriptsDir = new File(options.get(SCRIPTS_DIR));

        Preconditions.checkArgument(settingsFile.exists(), settingsFile.getAbsolutePath() + " does not exist. Use --capify to create it.");
        Preconditions.checkArgument(scriptsDir.exists(), scriptsDir.getAbsolutePath() + " does not exist. Use --capify to create it.");

        final File[] files = scriptsDir.listFiles(new PatternFilenameFilter("^.*\\.java$"));

        final ArrayList<String> params = new ArrayList<String>(8 + files.length);

        final File buildDir = new File(scriptsDir, "classes");

        if(!buildDir.exists()){
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

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        final int r = compiler.run(null, null, null, params.toArray(new String[params.size()]));

        if(r == 0){
            System.out.printf("compilation OK.%n");
        }else{
            System.out.printf("compilation failed.%n");
        }

        System.out.printf("configuring with Settings.java...%n");
        final URLClassLoader classLoader = new URLClassLoader(new URL[]{buildDir.toURI().toURL()});

        final Class<?> settingsClass = classLoader.loadClass("Settings");
        Callable c = (Callable) settingsClass.newInstance();
        c.call();

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

        deployScript.defaultTo("CreateNewScript");

        new Question("Enter a script name to run:",
            transform(loadedScriptClasses, new Function<Class<?>, String>() {
                public String apply(Class<?> input) {
                    return input.getSimpleName();
                }
            }),
            deployScript).ask();

        final Optional<Class<?>> scriptToRun = Iterables.tryFind(loadedScriptClasses, new Predicate<Class<?>>() {
            @Override
            public boolean apply(Class<?> input) {
                return input.getName().equals(localCtx().var(deployScript));
            }
        });

        if(scriptToRun.isPresent()){
            System.out.printf("running %s%n", scriptToRun.get().getSimpleName());

            final Script script = (Script) scriptToRun.get().newInstance();

            script.scriptsDir = scriptsDir;

            script.run();
        }else{
            System.err.printf("Didn't find script with name %s. Exiting.%n", localCtx().var(deployScript));
            System.exit(-1);
        }
//        System.out.println(localCtx.var(CapConstants.stages).getStages());
    }
}
