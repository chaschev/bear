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

package bear.core;

import bear.main.event.TextConsoleEventToUI;
import bear.main.phaser.OnceEnteredCallable;
import bear.plugins.Plugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.session.Variables;
import bear.task.*;
import chaschev.util.Exceptions;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

/**
 * Each script runs in a separate thread, because one thread execution must not suspend/fail all others.
 * <p/>
 * This is why vars are set in sessions. So todo is to implement set global var value. Note - it has completely different semantics, setting it inside a session could lead to an error
 * todo move shellContext and other to run context, separate BearScript parsing from executing
 */
public class BearScript2 {
    static final Logger logger = LoggerFactory.getLogger(BearScript2.class);
    static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    //    final DynamicVariable<BearScriptPhase> phaseId = new DynamicVariable<BearScriptPhase>();

    final GlobalContext global;
    Bear bear;


    public BearScript2(GlobalContext global, IBearSettings settings) {
        checkArgument(settings.isConfigured(), "settings not configured, call settings.configure(factory)");

        this.global = global;
        this.bear = global.bear;
    }

    static class ScriptSetVariable{
        final String name;
        final Object value;
        final String groovyExpression;
        final boolean global;
        final boolean remove;
        final boolean needsSave;

        public ScriptSetVariable(String name, BearScriptDirective d) {
            this.name = name;
            value = d.params.get("value");
            groovyExpression = d.getString("groovy");
            global = d.getBoolean("global");
            needsSave = d.getBoolean("save");
            remove = d.getBoolean("remove");
        }

        ScriptSetVariable(String name, Object value, String groovyExpression, boolean global, boolean remove, boolean needsSave) {
            this.name = name;
            this.value = value;
            this.groovyExpression = groovyExpression;
            this.global = global;
            this.remove = remove;
            this.needsSave = needsSave;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ScriptSetVariable that = (ScriptSetVariable) o;

            if (global != that.global) return false;
            if (needsSave != that.needsSave) return false;
            if (groovyExpression != null ? !groovyExpression.equals(that.groovyExpression) : that.groovyExpression != null)
                return false;
            if (!name.equals(that.name)) return false;
            if (value != null ? !value.equals(that.value) : that.value != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + (groovyExpression != null ? groovyExpression.hashCode() : 0);
            result = 31 * result + (global ? 1 : 0);
            result = 31 * result + (needsSave ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ScriptSetVariable{");
            sb.append("name='").append(name).append('\'');
            if(value!=null) sb.append(", value='").append(value).append('\'');
            if(groovyExpression != null) sb.append(", groovyExpression='").append(groovyExpression).append('\'');
            sb.append(", global=").append(global);
            sb.append('}');
            return sb.toString();
        }
    }

    private static Boolean getBoolean(Object x) {
        return x == null ? false : (Boolean) x;
    }


    public static final class ScriptError {
        String line;
        int index;
        String comment;

        public ScriptError(String line, int index, String comment) {
            this.line = line;
            this.index = index;
            this.comment = comment;
        }
    }

    public static class BearScriptParseResult {
        List<ScriptItem> scriptItems;
        List<ScriptError> globalErrors;

        public BearScriptParseResult(List<ScriptItem> scriptItems, List<ScriptError> globalErrors) {
            this.scriptItems = scriptItems;
            this.globalErrors = globalErrors;
        }
    }

    static class BearScriptDirective {
        final String directive;
        final String[] words;
        final Map<String, Object> params;
        final Optional<String> name;

        BearScriptDirective(String directive, Optional<String> name, String[] words, Map<String, Object> params) {
            this.directive = directive;
            this.name = name;
            this.words = words;
            this.params = params;
        }

        public String getString(String key) {
            return (String) params.get(key);
        }

        public boolean getBoolean(String key) {
            return params == null ? false : BearScript2.getBoolean(params.get(key));
        }
    }

    static final DirectiveParser directiveParser = new DirectiveParser();

    static BearScriptParseResult parseScript(String script, String initialPluginName) {
//        final List<String> currentScript = new ArrayList<String>();

        List<ScriptItem> scriptItems = new ArrayList<ScriptItem>();
        List<ScriptError> globalErrors = new ArrayList<ScriptError>();

//        String currentPluginName = initialPluginName;

        int lineIndex = 1;

        ScriptItem currentScriptItem = new ScriptItem(Optional.<String>absent(), initialPluginName, lineIndex);

        for (Iterator<String> iterator = Variables.LINE_SPLITTER.split(script).iterator(); iterator.hasNext(); lineIndex++) {
            String line = iterator.next();

            if (line.startsWith("#")) {
                continue;
            }

            if (line.startsWith(":") || line.startsWith("//!:")) {

                BearScriptDirective directive = directiveParser.parse(line);

                if(":set".equals(directive.directive)){
                    currentScriptItem.addVariable(directive.words[0], directive);
                }else
                if (":use".equals(directive.directive)) {
                    String command = directive.words[0];
                    String pluginName = directive.words[1];

                    if ("shell".equals(command)) {
                        if (!currentScriptItem.isEmpty()) {
                            scriptItems.add(currentScriptItem);
                        }

                        currentScriptItem = new ScriptItem(directive.name, pluginName, lineIndex);

                        currentScriptItem.global = directive.getBoolean("global");
                    } else {
                        ui.error(new TextConsoleEventToUI("shell", "command not supported: <i>" + command + "</i><br>"));

                        globalErrors.add(new ScriptError(line, lineIndex, "command not supported: " + command));
                    }

                    continue;
                } else
                if(":run".equals(directive.directive)){
                    scriptItems.add(scriptItemFromFileReference(directive));
                } else {
                    currentScriptItem.lines.add(line);
                }

                continue;
            }

            currentScriptItem.lines.add(line);
        }

        if (!currentScriptItem.isEmpty()) {
            scriptItems.add(currentScriptItem);
        }

        return new BearScriptParseResult(scriptItems, globalErrors);
    }

    static ScriptItem scriptItemFromFileReference(BearScriptDirective directive) {
        checkArgument(!directive.params.isEmpty(), "you need to provide params for script reference");

        try {
            String plugin = directive.getString("plugin");

            String scriptString = null;
            String scriptName = null;

            Map<String, Object> params = directive.params;

            if(params.containsKey("url")){
                String urlString = directive.getString("url");
                URL url = new URL(urlString);
                scriptString = Resources.asCharSource(url, Charsets.UTF_8).read();
                scriptName = FilenameUtils.getName(urlString);
            }else
            if(params.containsKey("file")){
                File file = new File(directive.getString("file"));

                scriptString = FileUtils.readFileToString(file);
                scriptName = file.getName();
            }


            if(params.containsKey("name")){
                scriptName = directive.getString("name");
            }

            if(plugin == null){
                plugin = FilenameUtils.getExtension(scriptName);

                checkArgument(!Strings.isNullOrEmpty(plugin), "could not detect plugin from filename: %s. please provide", scriptName);
            }

            checkArgument(!Strings.isNullOrEmpty(scriptName), "could detect not script name. please provide");

            return new ScriptItem(Optional.of(scriptName), plugin, Variables.LINE_SPLITTER.splitToList(scriptString), 0);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static class GroovyScriptSupplier implements Supplier<BearScriptParseResult> {

        private final BearScriptParseResult parseResult;

        public GroovyScriptSupplier(GlobalContext global, String groovyScript, Optional<String> scriptName) {
            GroovyShellPlugin groovy = global.getPlugin(GroovyShellPlugin.class);

            this.parseResult = new BearScriptParseResult(
                Lists.newArrayList(
                    new ScriptItem(scriptName, groovy.cmdAnnotation(), Variables.LINE_SPLITTER.splitToList(groovyScript), 1)
                ),
                Collections.<ScriptError>emptyList());

        }

        public GroovyScriptSupplier(GlobalContext global, File file) {
            try {
                GroovyShellPlugin groovy = global.getPlugin(GroovyShellPlugin.class);

                this.parseResult = new BearScriptParseResult(
                    Lists.newArrayList(
                        new ScriptItem(Optional.of(file.getName()), groovy.cmdAnnotation(), Variables.LINE_SPLITTER.splitToList(FileUtils.readFileToString(file)), 1)
                    ),
                    Collections.<ScriptError>emptyList());
            } catch (IOException e) {
                throw Exceptions.runtime(e);
            }

        }

        @Override
        public BearScriptParseResult get() {
            return parseResult;
        }
    }

    public static class ParserScriptSupplier implements Supplier<BearScriptParseResult> {
        private final Plugin initialPlugin;
        private final String script;

        public ParserScriptSupplier(Plugin initialPlugin, String script) {
            this.initialPlugin = initialPlugin;
            this.script = script;
        }

        @Override
        public BearScriptParseResult get() {
            return parseScript(script, initialPlugin.cmdAnnotation());
        }
    }

    public static Task<TaskDef> wrapIntoGlobalTask(final SessionContext $, final Task parent, final Task<?> task) {

        return new Task<TaskDef>(new TaskContext<TaskDef>(null, parent, $), new TaskCallable() {
            final OnceEnteredCallable<TaskResult> onceEnteredCallable = new OnceEnteredCallable<TaskResult>();

            @Override
            public TaskResult call(final SessionContext $, Task taskContext, Object input) throws Exception{
                return onceEnteredCallable.runOnce(new Callable<TaskResult>() {
                    @Override
                    public TaskResult call() throws Exception {
                        return $.runner.runSession(task);
                    }
                }).get();
            }
        });
    }


}
