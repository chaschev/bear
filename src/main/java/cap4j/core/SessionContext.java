package cap4j.core;

import cap4j.cli.CommandLine;
import cap4j.session.DynamicVariable;
import cap4j.session.SystemEnvironment;

/**
* User: chaschev
* Date: 7/29/13
*/
public class SessionContext {
//    public final GlobalContext globalContext;
    public final Variables sessionVariables;
    private final GlobalContext global;
    public final SystemEnvironment system;

    public SessionContext(GlobalContext global, SystemEnvironment system) {
        this.global = global;
        this.system = system;
        system.$ = this;
        this.sessionVariables = SystemEnvironment.newSessionVars(global, system);
        sessionVariables.putS(global.cap.sessionHostname, system.getName());
    }

    public GlobalContext getGlobal(){
        return global;
    }

    public SessionContext(Variables sessionVariables) {
        this.sessionVariables = sessionVariables;
        system = null;
        global = null;
    }

    public <T> T var(DynamicVariable<T> varName) {
        return sessionVariables.get(this, varName);
    }

    public String joinPath(DynamicVariable<String> var, String path){
        return system.joinPath(var(var), path);
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

    public boolean varB(DynamicVariable<Boolean> var) {
        return sessionVariables.get(this, var);
    }

    public CommandLine newCommandLine() {
        return system.newCommandLine();
    }


}
