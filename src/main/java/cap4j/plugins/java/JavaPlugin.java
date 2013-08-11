package cap4j.plugins.java;

import cap4j.core.CapConstants;
import cap4j.session.DynamicVariable;

/**
 * User: achaschev
 * Date: 8/4/13
 */
public class JavaPlugin {
    public static final DynamicVariable<String> javaHomePath = CapConstants.strVar("javaHomePath", "");
}
