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

import bear.core.*;
import bear.main.event.LogEventToUI;
import bear.main.event.NoticeEventToUI;
import bear.main.event.RMIEventToUI;
import bear.main.phaser.SettableFuture;
import bear.plugins.CommandInterpreter;
import bear.plugins.Plugin;
import bear.plugins.PomPlugin;
import bear.plugins.groovy.Replacement;
import bear.plugins.groovy.Replacements;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.task.*;
import chaschev.json.JacksonMapper;
import chaschev.lang.Lists2;
import chaschev.lang.Predicates2;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static com.google.common.collect.Lists.transform;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class FXConf extends BearMain {
    private static final Logger logger = LoggerFactory.getLogger(FXConf.class);
    private static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("ui");

    protected BearCommandInterpreter commandInterpreter;

    public FXConf(String... args) {
        super(GlobalContextFactory.INSTANCE.getGlobal(), createCompilerManager(), args);
    }

    public String getFileText(String className) {
        return compileManager.findClass(className).get().getText();
    }

    public void saveFileText(String className, String text) {
        compileManager.findClass(className).get().saveText(text);
    }

    public String getSelectedSettings() {
        return FilenameUtils.getBaseName($(projectFile).getName());
    }

    public Response run(String uiContextS) throws Exception {
        logger.info("running a script with params: {}", uiContextS);

        BearScriptRunner.UIContext uiContext = commandInterpreter.mapper.fromJSON(uiContextS, BearScriptRunner.UIContext.class);

        GlobalTaskRunner runner = run(newProject(uiContext.projectName).setInteractiveMode(), null, false, true);

        return sendHostsEtc(runner);
    }

    private Response sendHostsEtc(GlobalTaskRunner runner) {
        List<BearScriptRunner.RunResponse.Host> hosts = getHosts(runner.getSessions());

        ui.info(new RMIEventToUI("terminals", "onScriptStart", hosts));

        return new BearScriptRunner.RunResponse(runner, hosts);
    }

    public static List<BearScriptRunner.RunResponse.Host> getHosts(List<SessionContext> $s) {
        return transform($s, new Function<SessionContext, BearScriptRunner.RunResponse.Host>() {
            public BearScriptRunner.RunResponse.Host apply(SessionContext $) {
                return new BearScriptRunner.RunResponse.Host($.sys.getName(), $.sys.getAddress());
            }
        });
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

    public <V> Future<V> evaluateInFX(final Callable<V> callable) {
        final SettableFuture<V> future = new SettableFuture<V>();

        bearFX.bearFXApp.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    future.set(callable.call());
                } catch (Exception e) {
                    future.setException(e);
                }
            }
        });

        return future;
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
                bear.getGlobal().plugin(PomPlugin.class).generate(),
                Files.newWriterSupplier(file, Charsets.UTF_8)
            );

            ui.info(new NoticeEventToUI("Create POM",
                "POM has been created in " + file.getAbsolutePath()));
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    @Override
    public FXConf configure() throws IOException {
        super.configure();

        commandInterpreter = $.wire(new BearCommandInterpreter());
        commandInterpreter.switchToPlugin(GenericUnixRemoteEnvironmentPlugin.class);

        return this;
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

    public class BearCommandInterpreter {
        GlobalContext global;

        Plugin currentShellPlugin;

        public CommandInterpreter currentInterpreter() {
            return currentShellPlugin.getShell();
        }

        public final JacksonMapper mapper = new JacksonMapper();


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

            final BearScriptRunner.UIContext uiContext = mapper.fromJSON(uiContextS, BearScriptRunner.UIContext.class);

            final BearProject<?> project = newProject(uiContext.projectName)
                .setInteractiveMode();

            Plugin<TaskDef> shellPlugin = null;

            for (Plugin<TaskDef> plugin : project.getAllShellPlugins()) {
                if(uiContext.shell.equals(plugin.cmdAnnotation())){
                    shellPlugin = plugin;
                    break;
                }
            }

            final Plugin<TaskDef> finalShellPlugin = shellPlugin;

            GlobalTaskRunner runner = project.run(Collections.singletonList(new NamedCallable<Object, TaskResult>(new TaskCallable<Object, TaskResult>() {
                @Override
                public TaskResult call(SessionContext $, Task<Object, TaskResult> task) throws Exception {
                    Task<Object, TaskResult> interpretedTask = finalShellPlugin.getShell().interpret(command, $, task, task.getDefinition());
                    return $.runner.runSession(interpretedTask);
                }
            })));

            return sendHostsEtc(runner);
        }

        private void switchToPlugin(Class<? extends Plugin> aClass) {
            this.currentShellPlugin = global.plugin(aClass);
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
}
