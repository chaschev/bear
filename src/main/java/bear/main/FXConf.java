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

import bear.context.Cli;
import bear.context.Fun;
import bear.core.*;
import bear.main.event.LogEventToUI;
import bear.plugins.CommandInterpreter;
import bear.plugins.Plugin;
import bear.plugins.PomPlugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.plugins.groovy.Replacement;
import bear.plugins.groovy.Replacements;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.session.Question;
import chaschev.json.JacksonMapper;
import chaschev.lang.Lists2;
import chaschev.lang.Predicates2;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.*;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

import static chaschev.lang.Maps2.newHashMap;
import static com.google.common.collect.Lists.transform;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class FXConf extends Cli {
    private CompileManager compileManager;

    private static final Logger logger = LoggerFactory.getLogger(FXConf.class);
    private static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("ui");

    private BearCommandInterpreter commandInterpreter;

    public FXConf(String... args) {
        super(GlobalContextFactory.INSTANCE.getGlobal(), args);
    }

    public IBearSettings newSettings() {
        return newSettings($(settingsFile).getName());
    }

    public IBearSettings newSettings(String bearSettings) {
        try {
            final CompiledEntry settingsEntry = compileManager.findClass(bearSettings, false);

            Preconditions.checkNotNull(settingsEntry, "%s not found", bearSettings);

            IBearSettings settings = (IBearSettings) settingsEntry.newInstance(factory);

            settings.loadProperties($(newRunProperties));

            settings.configure(factory);

            return settings;
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    private Optional<CompiledEntry> compileAndLoadScript() throws MalformedURLException {
        CompilationResult result = compileManager.compileWithAll();

        return findScriptToRun((List) result.scriptClasses);
    }

    private Optional<CompiledEntry> findScriptToRun(List<CompiledEntry> compiledEntries) {
        if (isScriptNameSet()) {
            String scriptName = $(script).getName();

            BearMain.logger.info("script is set in the command line to {}", scriptName);
            bear.deployScript.defaultTo(scriptName);
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
        return $.isSet(script) && $.var(script) != Fun.UNDEFINED;
    }

    public String getScriptText() throws IOException {
        return FileUtils.readFileToString($(script));
    }

    public String getSettingsText() throws IOException {
        return FileUtils.readFileToString($(settingsFile));
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

    public String getSelectedSettings() {
        return FilenameUtils.getBaseName($(settingsFile).getName());
    }

    public Response run(String uiContextS) throws Exception {
        logger.info("running a script with params: {}", uiContextS);

        BearScript2.UIContext uiContext = commandInterpreter.mapper.fromJSON(uiContextS, BearScript2.UIContext.class);

        File file = compileManager.findScript(uiContext.script);

        Preconditions.checkNotNull(file.exists(), "%s not found", uiContext.script);

        String s = FileUtils.readFileToString(file);

        return runWithScript(s, uiContext.settingsName);
    }

    public Response runWithScript(String bearScript, String settingsName) throws Exception {
        IBearSettings settings = newSettings(settingsName);

        return new BearScript2(global, bearFX, null, settings).exec(true,
            new BearScript2.ParserScriptSupplier(null, bearScript));
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

    public String completeCode(String script, int caretPos) {
        return commandInterpreter.completeCode(script, caretPos);
    }

    public void evaluateInFX(Runnable runnable) {
        bearFX.bearFXApp.runLater(runnable);
    }

    public FileResponse getPropertyAsFile(String property) {
        String file = bearFX.bearProperties.getProperty(property);

        Preconditions.checkNotNull(file, "no such property: %s", property);

        if (file.indexOf('/') == -1 && file.indexOf('\'') == -1) {
            return new FileResponse(new File($(scriptsDir), file));
        }

        return new FileResponse(new File(file));
    }

    public void createPom() {
        try {
            File file = new File(".bear/pom.xml");

            logger.info("writing POM to {}...", file.getAbsolutePath());

            CharStreams.write(
                bear.getGlobal().getPlugin(PomPlugin.class).generate(),
                Files.newWriterSupplier(file, Charsets.UTF_8)
            );
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static class CompilationResult {
        public long timestamp;

        public CompilationResult() {
        }

        public List<CompiledEntry> scriptClasses = new ArrayList<CompiledEntry>();
        public List<CompiledEntry> settingsClasses = new ArrayList<CompiledEntry>();

        public CompilationResult mergeIn() {
            throw new UnsupportedOperationException();
        }
    }

    public static class FileResponse {
        public String dir;
        public String filename;
        public String path;
        public String absPath;

        public FileResponse(File file) {
            dir = file.getParent();
            filename = file.getName();
            path = file.getPath();
            absPath = file.getAbsolutePath();
        }
    }

    public class BearCommandInterpreter {
        GlobalContext global;

        Plugin currentShellPlugin;

        public CommandInterpreter currentInterpreter() {
            return currentShellPlugin.getShell();
        }

        final JacksonMapper mapper = new JacksonMapper();


        /**
         * Scope: GLOBAL
         *
         * : -> system command
         * :help
         * :use shell <plugin>
         *
         * @param command
         */
        public Response interpret(final String command, String uiContextS) throws Exception {
            ui.info("interpreting command: '{}', params: {}", StringUtils.substringBefore(command, "\n").trim(), uiContextS);

            final BearScript2.UIContext uiContext = mapper.fromJSON(uiContextS, BearScript2.UIContext.class);

            final IBearSettings settings = newSettings(uiContext.settingsName);

            Callable<BearScript2.MessageResponse> execScriptCallable = new Callable<BearScript2.MessageResponse>() {
                @Override
                public BearScript2.MessageResponse call() throws Exception {
                    final BearScript2 script = new BearScript2(global, bearFX, currentShellPlugin, settings);

                    Supplier<BearScript2.BearScriptParseResult> supplier;

                    if(uiContext.script.endsWith(".groovy")){
                        supplier = new BearScript2.GroovyScriptSupplier(global, command, Optional.fromNullable(uiContext.script));
                    } else {
                        supplier = new BearScript2.ParserScriptSupplier(currentShellPlugin, command);
                    }

                    script.exec(true, supplier);

                    return new BearScript2.MessageResponse("started script execution");
                }
            };

            if(!"shell".equals(uiContext.shell) && !"status".equals(uiContext.shell)){
                return global.withMap(
                    newHashMap(
                        bear.activeHosts, singletonList(uiContext.shell),
                        bear.activeRoles, emptyList()
                    ),
                    execScriptCallable
                );
            } else{
                return execScriptCallable.call();
            }
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

    @Override
    public Cli configure() throws IOException {
        super.configure();

        compileManager = new CompileManager($(scriptsDir), $(buildDir));

        $.localCtx().log("configuring Bear with default settings...");

        commandInterpreter = $.wire(new BearCommandInterpreter());
        commandInterpreter.switchToPlugin(GenericUnixRemoteEnvironmentPlugin.class);

        global.getPlugin(GroovyShellPlugin.class).getShell().set$(this);

        build();

        return this;
    }

    private String[] getNames(List classes) {
        return (String[]) Lists2.projectMethod(classes, "getName").toArray(new String[classes.size()]);
    }

    public void cancelAll(){
        GlobalTaskRunner runContext = global.currentGlobalRunner;

        if(runContext == null){
            ui.warn(new LogEventToUI("shell", "not running"));
            return;
        }

        List<SessionContext> entries = runContext.getSessions();

        for (SessionContext $ : entries) {
            if($.isRunning()){
                try {
                    $.terminate();
                } catch (Exception e) {
                    logger.warn("could not terminate", e);
                }
            }
        }
    }

    public void cancelThread(String name){
        GlobalTaskRunner runContext = global.currentGlobalRunner;

        if(runContext == null){
            ui.warn(new LogEventToUI("shell", "not running"));
            return;
        }

        SessionContext $ = Iterables.find(runContext.getSessions(), Predicates2.methodEquals("getName", name));

        $.terminate();
    }
}
