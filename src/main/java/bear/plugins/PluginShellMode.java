package bear.plugins;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class PluginShellMode implements CommandInterpreter {
    protected final Plugin plugin;
    protected String commandName;
    protected String description;

    protected PluginShellMode(Plugin plugin, String commandName) {
        this.plugin = plugin;
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
    }

    @Override
    public String toString() {
        return commandName;
    }

    /**
     * Called during the init phase of a plugin.
     */
    public void init(){

    }
}
