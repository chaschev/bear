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
        return GlobalContext.gvars().get(this, var, null);
    }

    public String varS(Nameable<String> varName) {
        return sessionVariables.get(this, varName, null);
    }

    public <T> T var(Nameable<T> varName) {
        return sessionVariables.get(this, varName, null);
    }

    public <T> T var(DynamicVariable<T> varName) {
        return sessionVariables.get(this, varName);
    }

    public String joinPath(Nameable<String> var, String path){
        return system.joinPath(varS(var), path);
    }

    public String joinPath(String... paths){
        return system.joinPath(paths);
    }

    public String threadName() {
        return system.getName();
    }

    public boolean varB(Nameable<Boolean> var) {
        return sessionVariables.get(this, var, null);
    }

}
