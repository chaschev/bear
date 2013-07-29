package cap4j.session;

import cap4j.Variables;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class SessionContext {
    public final Variables variables;

    public SessionContext(Variables variables) {
        this.variables = variables;
    }

    public SessionContext dup(){
        return new SessionContext(variables.dup());
    }
}
