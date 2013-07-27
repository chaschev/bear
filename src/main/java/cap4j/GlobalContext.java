package cap4j;

import cap4j.session.SystemEnvironments;
import cap4j.session.Variable;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class GlobalContext {
    public static GlobalContext INSTANCE;
    public final Variables variables = new Variables(null);
    public final Console console = new Console();

    public static class ConstantString extends Variable {
        String s;

        public ConstantString(String s) {
            this.s = s;
        }

        public Object apply(Variables.Context input) {
            return s;
        }
    }

    SystemEnvironments system;

    protected GlobalContext() {

    }

    public static Variables gvars(){
        return INSTANCE.variables;
    }

    public static String var(Nameable varName){
        return INSTANCE.variables.get(varName, null);
    }

    public static <T> T var(Nameable varName, T _default){
        return INSTANCE.variables.get(varName, _default);
    }

    public static Console console(){
        return INSTANCE.console;
    }
}
