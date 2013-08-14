package atocha;

import cap4j.core.CapConstants;
import cap4j.session.DynamicVariable;

/**
* User: achaschev
* Date: 8/13/13
* Time: 8:02 PM
*/
public enum AtochaConstants {
    INSTANCE;

    public final DynamicVariable<Boolean>
        reuseWar = CapConstants.bool("reuseWar", "will skip building WAR").defaultTo(false);

}
