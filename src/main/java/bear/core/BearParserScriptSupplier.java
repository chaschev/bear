package bear.core;

import bear.main.event.TextConsoleEventToUI;
import bear.plugins.Plugin;
import bear.session.Variables;
import chaschev.util.Exceptions;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * Each script runs in a separate thread, because one thread execution must not suspend/fail all others.
 * <p/>
 * This is why vars are set in sessions. So todo is to implement set global var value. Note - it has completely different semantics, setting it inside a session could lead to an error
 * todo move shellContext and other to run context, separate BearScript parsing from executing
 */


/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class BearParserScriptSupplier implements Supplier<BearParserScriptSupplier.BearScriptParseResult> {
    static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");
    static final DirectiveParser directiveParser = new DirectiveParser();
    private final Plugin initialPlugin;
    private final String script;

    public BearParserScriptSupplier(Plugin initialPlugin, String script) {
        this.initialPlugin = initialPlugin;
        this.script = script;
    }

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

    @Override
    public BearScriptParseResult get() {
        return parseScript(script, initialPlugin.cmdAnnotation());
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
            return params == null ? false : BearParserScriptSupplier.getBoolean(params.get(key));
        }
    }

    private static Boolean getBoolean(Object x) {
        return x == null ? false : (Boolean) x;
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
}
