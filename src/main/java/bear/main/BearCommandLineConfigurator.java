package bear.main;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.GlobalContextFactory;
import bear.core.IBearSettings;
import bear.plugins.CommandInterpreter;
import bear.plugins.Plugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.plugins.groovy.Replacement;
import bear.plugins.groovy.Replacements;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.session.Question;
import chaschev.json.JacksonMapper;
import chaschev.lang.Lists2;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static bear.main.BearMain.Options.*;
import static chaschev.lang.LangUtils.elvis;
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

    private BearCommandInterpreter commandInterpreter;

    private CompileManager compileManager;

    protected BearFX bearFX;
    private File file;

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

        commandInterpreter = global.wire(new BearCommandInterpreter());
        commandInterpreter.switchToPlugin(GenericUnixRemoteEnvironmentPlugin.class);

        global.getPlugin(GroovyShellPlugin.class).getShell().set$(this);

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

    public Response run(String uiContextS) throws Exception {
        logger.info("running a script with params: {}", uiContextS);

        BearScript.UIContext uiContext = commandInterpreter.mapper.fromJSON(uiContextS, BearScript.UIContext.class);

        file = compileManager.findScript(uiContext.scriptName);

        Preconditions.checkNotNull(file.exists(), "%s not found", uiContext.scriptName);

        String s = FileUtils.readFileToString(file);

        return runWithScript(s, uiContext.settingsName);
    }

    public Response runWithScript(String bearScript, String settingsName) throws Exception {
        IBearSettings settings = newSettings(settingsName);

        return new BearScript(global, bearFX, null, settings).exec(bearScript);
    }

    public class BearCommandInterpreter {
        GlobalContext global;

        Plugin currentShellPlugin;

        public CommandInterpreter currentInterpreter() {
            return currentShellPlugin.getShell();
        }

        final JacksonMapper mapper = new JacksonMapper();


        /**
         * : -> system command
         * :help
         * :use shell <plugin>
         *
         * @param command
         */
        public Response interpret(final String command, String uiContextS) throws Exception {
            logger.info("interpreting command: {}, params: {}", command, uiContextS);

            BearScript.UIContext uiContext = mapper.fromJSON(uiContextS, BearScript.UIContext.class);

            IBearSettings settings = newSettings(uiContext.settingsName);
            settings.configure(factory);

            final BearScript script = new BearScript(global, bearFX, currentShellPlugin, settings);

            global.putConst(bear.internalInteractiveRun, true);

            global.taskExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Response exec = script.exec(command);
                    currentShellPlugin = script.currentPlugin;
                    global.removeConst(bear.internalInteractiveRun);
                }
            });

            return new BearScript.MessageResponse("started script execution");
        }

        private void switchToPlugin(Class<? extends Plugin> aClass) {
            this.currentShellPlugin = global.getPlugin(aClass);
        }

        public String completeCode(String script, int caretPos) {
            try {
                Replacements replacements = currentInterpreter().completeCode(script, caretPos);

                StringWriter writer = new StringWriter(1024);
                JsonGenerator g = mapper.getMapper().getFactory().createGenerator(writer);

                g.writeStartArray();

                for (Replacement replacement : replacements.getReplacements()) {
                    g.writeStartObject();
                    g.writeStringField("caption", replacement.name);
                    g.writeStringField("meta", replacement.type);
                    g.writeStringField("snippet", replacement.snippet);
                    g.writeEndObject();
                }

                g.writeEndArray();

                g.close();

                return writer.toString();
            } catch (IOException e) {
                throw Exceptions.runtime(e);
            }
        }
    }

    public Response interpret(String command, String uiContextS) throws Exception {
        return commandInterpreter.interpret(command, uiContextS);
    }

    public String pasteFromClipboard() {
        return Clipboard.getSystemClipboard().getString();
    }

    public void copyToClipboard(String text) {
        HashMap<DataFormat, Object> map = new HashMap<DataFormat, Object>();
        map.put(DataFormat.PLAIN_TEXT, text);
        Clipboard.getSystemClipboard().setContent(map);
    }

    public String completeCode(String script, int caretPos){
        return commandInterpreter.completeCode(script, caretPos);
    }

    public void evaluateInFX(Runnable runnable){
        bearFX.bearFXApp.runLater(runnable);
    }
}
