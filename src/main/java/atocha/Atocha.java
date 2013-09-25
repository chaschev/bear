package atocha;

import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;
import cap4j.task.Task;

/**
* User: achaschev
* Date: 8/13/13
* Time: 8:02 PM
*/
public class Atocha extends Plugin {

    public final DynamicVariable<Boolean>
        reuseWar = Cap.bool("will skip building WAR").defaultTo(false);

    public Atocha(GlobalContext global) {
        super(global);
    }

    @Override
    public Task getSetup() {
        return null;
    }
}
