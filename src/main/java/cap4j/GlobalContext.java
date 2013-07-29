package cap4j;

import cap4j.session.GenericUnixLocalEnvironment;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import org.apache.commons.lang3.SystemUtils;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class GlobalContext {
    public static GlobalContext INSTANCE;
    public final Variables variables = new Variables(null);
    public final Console console = new Console();

    public final SystemEnvironment local = SystemUtils.IS_OS_WINDOWS ?
        new GenericUnixLocalEnvironment("local") : new GenericUnixLocalEnvironment("local");

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

    public static SystemEnvironment local(){
        return INSTANCE.local;
    }


}
