package bear.core;

import bear.context.AbstractContext;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static chaschev.lang.Predicates2.contains;
import static chaschev.lang.Predicates2.fieldEquals;
import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.of;
import static com.google.common.base.Predicates.not;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class ScriptItem {
    public static final Predicate<BearScript2.ScriptSetVariable> IS_GLOBAL = fieldEquals("global", Boolean.TRUE);

    Optional<String> scriptName;
    int startsAtIndex;
    String pluginName;
    final List<String> lines;

    Optional<HashMap<String, BearScript2.ScriptSetVariable>> variables = absent();

    /**
     * For groovy global means that item will be wrapped into RunOnce and
     * executed with a GlobalContext.
     */
    public boolean global;

    ScriptItem(Optional<String> scriptName, String pluginName, int startsAtIndex) {
        this.scriptName = scriptName;
        this.pluginName = pluginName;
        this.startsAtIndex = startsAtIndex;
        lines = new ArrayList<String>();
    }

    ScriptItem(Optional<String> scriptName, String pluginName, List<String> lines, int startsAtIndex) {
        if (scriptName.isPresent()) {
            String s = scriptName.get();
            if(s.indexOf('/') !=-1 || s.indexOf('\\') != -1){
                scriptName = of(FilenameUtils.getName(s));
            }
        }

        this.scriptName = scriptName;
        this.pluginName = pluginName;
        this.lines = lines;
        this.startsAtIndex = startsAtIndex;
    }

    String getScriptName() {
        return scriptName.or(asOneLineDesc());
    }

    public String asOneLineDesc() {
        Optional<String> nonCommandLine = Iterables.tryFind(lines, not(contains(":set")));

        String line = nonCommandLine.or(lines.get(0));

        return pluginName + ": " + line;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public ScriptItem addVariable(String name, BearScript2.BearScriptDirective directive) {

        if(!variables.isPresent()){
            variables = of(new HashMap<String, BearScript2.ScriptSetVariable>());
        }

        variables.get().put(name, new BearScript2.ScriptSetVariable(name, directive));

        return this;
    }

    public void assignVariables(SessionContext $) {
        assignVars($, not(IS_GLOBAL));
    }

    public void assignVariables(GlobalContext $) {
        assignVars($, IS_GLOBAL);
    }

    private void assignVars(AbstractContext $, Predicate<BearScript2.ScriptSetVariable> filter) {
        if(variables.isPresent()){
            for (Map.Entry<String, BearScript2.ScriptSetVariable> e : variables.get().entrySet()) {
                BearScript2.ScriptSetVariable var = e.getValue();

                if(var.remove){
                    $.removeConst(var.name);
                    continue;
                }

                if(!filter.apply(var)) continue;

                if(var.value != null){
                    $.putConst(e.getKey(), var.value);
                }else{
                    throw new UnsupportedOperationException("groovy expressions in var directives are not yet supported!");
                }
            }
        }
    }
}
