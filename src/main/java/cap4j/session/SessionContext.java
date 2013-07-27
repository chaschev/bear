package cap4j.session;

import cap4j.GlobalContext;
import cap4j.Variables;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class SessionContext {
    public final Variables variables;

    protected SessionContext(Variables variables) {
        this.variables = variables;
    }

    public SessionContext(GlobalContext global) {
        variables = new Variables(global);
    }

    public SessionContext dup(){
        return new SessionContext(variables.dup());
    }
}
