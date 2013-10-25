package bear.plugins;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.Stage;
import bear.plugins.groovy.Replacements;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class PluginShellMode<T extends Plugin> implements CommandInterpreter {
    protected final T plugin;
    protected String commandName;
    protected String description;

    protected GlobalContext global;
    protected Bear bear;

    protected PluginShellMode(T plugin, String commandName) {
        this.plugin = plugin;
        this.commandName = commandName;
        this.global = plugin.getGlobal();
        this.bear = global.bear;
    }

    public String getCommandName() {
        return commandName;
    }

    @Override
    public String toString() {
        return commandName;
    }

    @Override
    public Stage getStage() {
        return null;
    }

    /**
     * Called during the init phase of a plugin.
     */
    public void init(){

    }

    public T getPlugin() {
        return plugin;
    }

    @Override
    public Replacements completeCode(String script, int position) {
        return Replacements.EMPTY;
    }

    /**
     * JS, Groovy, Java are multi-line. Sh is not.
     * @return
     */
    public boolean multiLine(){
        return true;
    }
}
