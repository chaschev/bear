package bear.main;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.GlobalContextFactory;
import bear.core.IBearSettings;
import bear.session.Question;
import chaschev.util.Exceptions;
import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.PatternFilenameFilter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
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
    private boolean shouldExit;
    private String[] args;
    private File scriptsDir;
    private URLClassLoader classLoader;
    private GlobalContext global;
    private Bear bear;
    private Optional<Class<? extends Script>> scriptToRun;
    private GlobalContextFactory factory = GlobalContextFactory.INSTANCE;
    private BearMain.Options options;
    private File settingsFile;
    @Nullable
    private Properties properties;
    private String scriptName;

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

    public URLClassLoader getClassLoader() {
        return classLoader;
    }

    public IBearSettings newSettings() {
        return newSettings(getBaseName(settingsFile.getName()), new File(scriptsDir, "settings.properties"));
    }

    public IBearSettings newSettings(String bearSettings, File file){
        try {
            final Class<?> settingsClass = classLoader.loadClass(bearSettings);

            IBearSettings settings = (IBearSettings) settingsClass.newInstance();

            if(properties != null){
                settings.loadProperties(properties);
            }else{
                settings.loadProperties(file);
            }

            return  settings;
        } catch (Exception e){
            throw Exceptions.runtime(e);
        }
    }

    public GlobalContext getGlobal() {
        return global;
    }

    public Bear getBear() {
        return bear;
    }

    public Optional<Class<? extends Script>> getScriptToRun() {
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

        settingsFile = elvis(settingsFile, new File(options.get(SETTINGS_FILE)));
        scriptName = elvis(scriptName, options.get(SCRIPT));

        scriptsDir = new File(options.get(SCRIPTS_DIR));

        fileRequired(scriptsDir);

        global.localCtx().log("configuring Bear with default settings...");

        scriptToRun = compileAndLoadScript();

        return this;
    }

    public static class CompilationResult{
        public List<Class<? extends Script>> scriptClasses = new ArrayList<Class<? extends Script>>();
        public List<Class<? extends IBearSettings>> settingsClasses = new ArrayList<Class<? extends IBearSettings>>();
    }

    private Optional<Class<? extends Script>> compileAndLoadScript() throws MalformedURLException {
        CompilationResult result = compile();

        return findScriptToRun(result.scriptClasses);
    }

    public CompilationResult compile()  {
        final List<File> filesList = compileScripts();

        System.out.printf("configuring with %s...%n", getSettingsFile());

        try {
            classLoader = new URLClassLoader(new URL[]{getBuildDir().toURI().toURL()});
        } catch (MalformedURLException e) {
            throw Exceptions.runtime(e);
        }

        CompilationResult result = new CompilationResult();

        for (File file : filesList) {
            try {
                Class aClass = (Class) classLoader.loadClass(getBaseName(file.getName()));

                if(Script.class.isAssignableFrom(aClass)){
                    result.scriptClasses.add(aClass);
                } else
                if(IBearSettings.class.isAssignableFrom(aClass)){
                    result.settingsClasses.add(aClass);
                }
            } catch (ClassNotFoundException e) {
                throw Exceptions.runtime(e);
            }
        }

        return result;
    }

    private Optional<Class<? extends Script>> findScriptToRun(List<Class<? extends Script>> loadedScriptClasses) {
        if (isScriptNameSet()) {
            BearMain.logger.info("script is set in the command line to {}", getScriptName());
            bear.deployScript.defaultTo(getScriptName());
        } else {
            new Question("Enter a script name to run:",
                transform(loadedScriptClasses, new Function<Class<?>, String>() {
                    public String apply(Class<?> input) {
                        return input.getSimpleName();
                    }
                }),
                bear.deployScript).ask();
        }

        return Iterables.tryFind(loadedScriptClasses, new Predicate<Class<? extends Script>>() {
            @Override
            public boolean apply(Class<? extends Script> input) {
                return input.getName().equals(global.var(bear.deployScript));
            }
        });
    }

    private boolean isScriptNameSet() {
        return options.has(SCRIPT) || scriptName != null;
    }

    private String getScriptName() {
        return elvis(scriptName, options.get(SCRIPT));
    }

    private File fileRequired(File settingsFile) {
        Preconditions.checkArgument(settingsFile.exists(), settingsFile.getAbsolutePath() + " does not exist. Use --bearify to create it.");
        return settingsFile;
    }

    public List<File> compileScripts() {
        final File settingsFile = fileRequired(getSettingsFile());

        final File[] files = scriptsDir.listFiles(new PatternFilenameFilter("^.*\\.java$"));

        final ArrayList<String> params = newArrayListWithExpectedSize(files.length);

        final File buildDir = getBuildDir();

        if (!buildDir.exists()) {
            buildDir.mkdir();
        }

        Collections.addAll(params, "-d", buildDir.getAbsolutePath());
        final List<File> filesList = Lists.asList(settingsFile, files);

        final List<String> filePaths = Lists.transform(filesList, new Function<File, String>() {
            public String apply(File input) {
                shouldExit = true;
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

        return filesList;
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
}
