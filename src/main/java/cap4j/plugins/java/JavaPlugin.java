package cap4j.plugins.java;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;

/**
 * User: achaschev
 * Date: 8/4/13
 */
public class JavaPlugin extends Plugin {
    public final DynamicVariable<String> homePath = CapConstants.strVar("homePath", "");

    public JavaPlugin(GlobalContext global) {
        super(global);
    }
}
