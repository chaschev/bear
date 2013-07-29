package cap4j;

import cap4j.session.DynamicVariable;
import cap4j.session.SystemEnvironment;

/**
* User: chaschev
* Date: 7/29/13
*/
public class VarContext {
//    public final GlobalContext globalContext;
    public final Variables sessionVariables;
    public final SystemEnvironment system;

    public VarContext(Variables sessionVariables, SystemEnvironment system) {
        this.sessionVariables = sessionVariables;
        this.system = system;
    }


    public <T> T gvar(DynamicVariable<T> var) {
        return GlobalContext.gvars().get(var, null);
    }

    public String varS(Nameable varName) {
        return sessionVariables.get(varName, null);
    }

    public Object var(Nameable varName) {
        return sessionVariables.get(varName, null);
    }

    public String joinPath(Nameable var, String path){
        return system.joinPath(varS(var), path);
    }

    public String joinPath(String... paths){
        return system.joinPath(paths);
    }

    public String threadName() {
        return system.getName();
    }

    public boolean varB(Nameable var) {
        return sessionVariables.get(var, null);
    }
}
