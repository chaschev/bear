package bear.core;

import bear.plugins.groovy.GroovyShellPlugin;
import bear.session.Variables;
import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class GroovyScriptSupplier implements Supplier<BearParserScriptSupplier.BearScriptParseResult> {

    private final BearParserScriptSupplier.BearScriptParseResult parseResult;

    public GroovyScriptSupplier(GlobalContext global, String groovyScript, Optional<String> scriptName) {
        GroovyShellPlugin groovy = global.getPlugin(GroovyShellPlugin.class);

        this.parseResult = new BearParserScriptSupplier.BearScriptParseResult(
            Lists.newArrayList(
                new ScriptItem(scriptName, groovy.cmdAnnotation(), Variables.LINE_SPLITTER.splitToList(groovyScript), 1)
            ),
            Collections.<BearParserScriptSupplier.ScriptError>emptyList());

    }

    public GroovyScriptSupplier(GlobalContext global, File file) {
        try {
            GroovyShellPlugin groovy = global.getPlugin(GroovyShellPlugin.class);

            this.parseResult = new BearParserScriptSupplier.BearScriptParseResult(
                Lists.newArrayList(
                    new ScriptItem(Optional.of(file.getName()), groovy.cmdAnnotation(), Variables.LINE_SPLITTER.splitToList(FileUtils.readFileToString(file)), 1)
                ),
                Collections.<BearParserScriptSupplier.ScriptError>emptyList());
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }

    }

    @Override
    public BearParserScriptSupplier.BearScriptParseResult get() {
        return parseResult;
    }
}
