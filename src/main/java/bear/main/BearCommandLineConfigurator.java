package bear.main;

import bear.console.CompositeConsoleArrival;
import bear.core.*;
import bear.session.DynamicVariable;
import bear.session.Question;
import bear.task.Task;
import bear.task.exec.CommandExecutionEntry;
import chaschev.lang.Lists2;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static bear.main.BearMain.Options.*;
import static chaschev.lang.LangUtils.elvis;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.google.common.collect.Lists.transform;
import static org.apache.commons.io.FilenameUtils.getBaseName;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearCommandLineConfigurator {
    public static final Logger logger = LoggerFactory.getLogger(BearCommandLineConfigurator.class);

    private boolean shouldExit;
    private String[] args;
    private File scriptsDir;
    private GlobalContext global;
    private Bear bear;
    private Optional<CompiledEntry> scriptToRun;
    private GlobalContextFactory factory = GlobalContextFactory.INSTANCE;
    private BearMain.Options options;
    private File settingsFile;
    @Nullable
    private Properties properties;
    private String scriptName;
    private String propertiesName;

    private CompileManager compileManager;

    protected BearFX bearFX;

    public BearCommandLineConfigurator(String... args) {
        this.args = args;
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

    public File getScriptsDir() {
        return scriptsDir;
    }

    public IBearSettings newSettings() {
        return newSettings(getBaseName(settingsFile.getName()));
    }

    public IBearSettings newSettings(String bearSettings) {
        return newSettings(bearSettings, new File(scriptsDir, propertiesName));
    }


    public IBearSettings newSettings(String bearSettings, @Nonnull File file) {
        Preconditions.checkNotNull(file);

        try {
            final CompiledEntry settingsEntry = compileManager.findClass(bearSettings, false);

            Preconditions.checkNotNull(settingsEntry, "%s not found", bearSettings);

            IBearSettings settings = (IBearSettings) settingsEntry.newInstance(factory);

            if (properties != null) {
                settings.loadProperties(properties);
            } else {
                settings.loadProperties(file);
            }

            return settings;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public GlobalContext getGlobal() {
        return global;
    }

    public Bear getBear() {
        return bear;
    }

    public Optional<CompiledEntry> getScriptToRun() {
        return scriptToRun;
    }

    public void setSettingsFile(File settingsFile) {
        this.settingsFile = settingsFile;
    }

    public BearCommandLineConfigurator configure() throws IOException {
        final GlobalContextFactory factory = GlobalContextFactory.INSTANCE;
        global = factory.getGlobal();
        bear = global.bear;

        options = new BearMain.Options(args);

        if (options.has(HELP)) {
            System.out.println(options.printHelpOn());
            shouldExit = true;
            return this;
        }

        if (options.has(BEARIFY)) {
            final File bearDir = new File(options.get(BEARIFY), options.get(SCRIPTS_DIR));
//            System.out.printf("saving to dir %s%n", bearDir.getAbsolutePath());;
            if (!bearDir.exists()) {
                bearDir.mkdirs();
            }
            copyResource("CreateNewScript.java", bearDir);
            copyResource("BearSettings.java", bearDir);
            copyResource("settings.properties.rename", "settings.properties", bearDir);
            shouldExit = true;
            return this;
        }

        settingsFile = elvis(settingsFile, new File(scriptsDir, options.get(SETTINGS_FILE)));
        scriptName = elvis(scriptName, options.get(SCRIPT));
        propertiesName = elvis(propertiesName, options.get(PROPERTIES_FILE));

        scriptsDir = new File(options.get(SCRIPTS_DIR));

        compileManager = new CompileManager(getScriptsDir(), getBuildDir());

        fileRequired(scriptsDir);

        global.localCtx().log("configuring Bear with default settings...");

//        scriptToRun = compileAndLoadScript();

        return this;
    }

    public static class CompilationResult {
        public List<CompiledEntry> scriptClasses = new ArrayList<CompiledEntry>();
        public List<CompiledEntry> settingsClasses = new ArrayList<CompiledEntry>();

        public CompilationResult mergeIn() {
            throw new UnsupportedOperationException();
        }
    }

    private Optional<CompiledEntry> compileAndLoadScript() throws MalformedURLException {
        CompilationResult result = compileManager.compileWithAll();

        return findScriptToRun((List) result.scriptClasses);
    }

    public static abstract class Compiler {
        protected final String[] extensions = extensions();

        protected File sourcesDir;
        protected File buildDir;

        public abstract String[] extensions();

        protected Compiler(File sourcesDir, File buildDir) {
            this.sourcesDir = sourcesDir;
            this.buildDir = buildDir;
        }

        public boolean accepts(String extension) {
            return ArrayUtils.contains(extensions, extension);
        }

        public abstract CompilationResult compile();
    }

    public static class CompileManager {
        protected final File sourcesDir;
        protected final File buildDir;
        protected final JavaCompiler2 javaCompiler;
        private CompilationResult lastCompilationResult;

        public CompileManager(File sourcesDir, File buildDir) {
            this.sourcesDir = sourcesDir;
            this.buildDir = buildDir;
            this.javaCompiler = new JavaCompiler2(sourcesDir, buildDir);
        }

        public CompilationResult compileWithAll() {
            logger.info("compiling...");
            return lastCompilationResult = javaCompiler.compile();
        }

        public CompiledEntry findClass(final String className) {
            CompiledEntry aClass = findClass(className, true);

            if (aClass == null) {
                aClass = findClass(className, false);

                if (aClass == null) {
                    throw new RuntimeException("class not found: " + className);
                }

                return aClass;
            } else {
                return aClass;
            }
        }

        public CompiledEntry findClass(final String className, boolean script) {
            Preconditions.checkNotNull(lastCompilationResult, "you need to compile first to load classes");

            return Iterables.find(script ?
                lastCompilationResult.scriptClasses : lastCompilationResult.settingsClasses, new Predicate<CompiledEntry>() {
                @Override
                public boolean apply(CompiledEntry input) {
                    return input.aClass.getSimpleName().equals(className);
                }
            }, null);
        }
    }

    public static class JavaCompiler2 extends Compiler {
        private URLClassLoader classLoader;

        protected JavaCompiler2(File sourcesDir, File buildDir) {
            super(sourcesDir, buildDir);
        }

        @Override
        public String[] extensions() {
            return new String[]{"java"};
        }

        public CompilationResult compile() {
            final List<File> filesList = compileScripts(sourcesDir);

            try {
                classLoader = new URLClassLoader(new URL[]{buildDir.toURI().toURL()});
            } catch (MalformedURLException e) {
                throw Exceptions.runtime(e);
            }

            CompilationResult result = new CompilationResult();

            for (File file : filesList) {
                try {
                    Class aClass = (Class) classLoader.loadClass(getBaseName(file.getName()));

                    if (Script.class.isAssignableFrom(aClass)) {
                        result.scriptClasses.add(new CompiledEntry(aClass, file, "java"));
                    } else if (IBearSettings.class.isAssignableFrom(aClass)) {
                        result.settingsClasses.add(new CompiledEntry(aClass, file, "java"));
                    }

                } catch (ClassNotFoundException e) {
                    throw Exceptions.runtime(e);
                }
            }

            return result;
        }

        public List<File> compileScripts(File sourcesDir) {
            FileFilter filter = new SuffixFileFilter(extensions);

            final File[] files = sourcesDir.listFiles(filter);

            final ArrayList<String> params = newArrayListWithExpectedSize(files.length);

            if (!buildDir.exists()) {
                buildDir.mkdir();
            }

            Collections.addAll(params, "-d", buildDir.getAbsolutePath());
            final List<File> filesList = Lists.newArrayList(files);

            final List<String> filePaths = Lists.transform(filesList, new Function<File, String>() {
                public String apply(File input) {
                    return input.getAbsolutePath();
                }
            });

            params.addAll(filePaths);

            logger.info("compiling {}", params);

            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

            final int r = compiler.run(null, null, null, params.toArray(new String[params.size()]));

            if (r == 0) {
                logger.info("compilation OK.");
            } else {
                logger.info("compilation failed.");
            }

            return filesList;
        }
    }


    private Optional<CompiledEntry> findScriptToRun(List<CompiledEntry> compiledEntries) {
        if (isScriptNameSet()) {
            BearMain.logger.info("script is set in the command line to {}", getScriptName());
            bear.deployScript.defaultTo(getScriptName());
        } else {
            new Question("Enter a script name to run:",
                transform(compiledEntries, new Function<CompiledEntry, String>() {
                    public String apply(CompiledEntry input) {
                        return input.aClass.getSimpleName();
                    }
                }),
                bear.deployScript).ask();
        }

        return Iterables.tryFind(compiledEntries, new Predicate<CompiledEntry>() {
            @Override
            public boolean apply(CompiledEntry input) {
                return input.aClass.getName().equals(global.var(bear.deployScript));
            }
        });
    }

    private boolean isScriptNameSet() {
        return options.has(SCRIPT) || scriptName != null;
    }

    private String getScriptName() {
        return elvis(scriptName, options.get(SCRIPT));
    }

    private static File fileRequired(File settingsFile) {
        Preconditions.checkArgument(settingsFile.exists(), settingsFile.getAbsolutePath() + " does not exist. Use --bearify to create it.");
        return settingsFile;
    }


    public File getSettingsFile() {
        return settingsFile;
    }

    private File getBuildDir() {
        return new File(scriptsDir, "classes");
    }

    public GlobalContextFactory getFactory() {
        return factory;
    }

    public BearCommandLineConfigurator setProperties(Properties properties) {
        this.properties = properties;
        return this;
    }

    public BearCommandLineConfigurator setScriptName(String scriptName) {
        this.scriptName = scriptName;
        return this;
    }

    public String getScriptText() throws IOException {
        return FileUtils.readFileToString(getScriptsFile());
    }

    private File getScriptsFile() {
        return new File(scriptsDir, scriptName);
    }

    public String getSettingsText() throws IOException {
        return FileUtils.readFileToString(settingsFile);
    }

    public String[] getScriptNames() {
        return getNames(compileManager.lastCompilationResult.scriptClasses);
    }

    public String[] getSettingsNames() {
        return getNames(compileManager.lastCompilationResult.settingsClasses);
    }

    public String getFileText(String className) {
        return compileManager.findClass(className).getText();
    }

    public void saveFileText(String className, String text) {
        compileManager.findClass(className).saveText(text);
    }

    public void build() {

        compileManager.compileWithAll();
    }

    public String getSelectedScript() {
        return getScriptName();
    }

    public String getSelectedSettings() {
        return FilenameUtils.getBaseName(getSettingsFile().getName());
    }

    private String[] getNames(List classes) {
        return (String[]) Lists2.projectMethod(classes, "getName").toArray(new String[classes.size()]);
    }

    public static class RunResponse {
        public static class Host{
            public String name;
            public String address;

            public Host(String name, String address) {
                this.name = name;
                this.address = address;
            }
        }
        public List<Host> hosts;

        public RunResponse(List<Host> hosts) {
            this.hosts = hosts;
        }
    }

    public RunResponse run(String scriptName, String settingsName) throws Exception {
        logger.info("running script {}, settings: {}", scriptName, settingsName);

        CompiledEntry scriptEntry = compileManager.findClass(scriptName, true);

        Preconditions.checkNotNull(scriptEntry, "%s not found", scriptName);

        Script script = (Script) scriptEntry.aClass.newInstance();

        IBearSettings settings = newSettings(settingsName);

        CompositeTaskRunContext context = new BearRunner(settings, script, factory)
            .init()
            .prepareToRun();

        CompositeConsoleArrival<SessionContext> consoleArrival = context.getConsoleArrival();

        List<SessionContext> $s = consoleArrival.getEntries();

        for (final SessionContext $ : $s) {
            SessionContext.ExecutionContext execContext = $.getExecutionContext();

            execContext.textAppended.addListener(new DynamicVariable.ChangeListener<String>() {
                public void changedValue(DynamicVariable<String> var, String oldValue, String newValue) {
                    if (StringUtils.isNotEmpty(newValue)) {
                        bearFX.bearFXApp.sendMessageToUI(new TextConsoleEventToUI($.getName(), newValue));
                    }
                }
            });

            execContext.currentTask.addListener(new DynamicVariable.ChangeListener<Task>() {
                @Override
                public void changedValue(DynamicVariable<Task> var, Task oldValue, Task newValue) {
                    bearFX.bearFXApp.sendMessageToUI(new TaskConsoleEventToUI($.getName(), newValue.toString()));
                }
            });

            execContext.currentCommand.addListener(new DynamicVariable.ChangeListener<CommandExecutionEntry>() {
                @Override
                public void changedValue(DynamicVariable<CommandExecutionEntry> var, CommandExecutionEntry oldValue, CommandExecutionEntry newValue) {
                    bearFX.bearFXApp.sendMessageToUI(new CommandConsoleEventToUI($.getName(), newValue.toString()));
                }
            });
        }

        context.stats.addListener(new DynamicVariable.ChangeListener<CompositeTaskRunContext.Stats>() {
            @Override
            public void changedValue(DynamicVariable<CompositeTaskRunContext.Stats> var, CompositeTaskRunContext.Stats oldValue, CompositeTaskRunContext.Stats newValue) {
                bearFX.bearFXApp.sendMessageToUI(new GlobalStatusEventToUI(newValue));
            }
        });

        context.submitTasks();

        return new RunResponse(Lists.transform(context.getConsoleArrival().getEntries(), new Function<SessionContext, RunResponse.Host>() {
            public RunResponse.Host apply(SessionContext $) {
                return new RunResponse.Host($.sys.getName(), $.sys.getAddress());
            }
        }));
    }


}
