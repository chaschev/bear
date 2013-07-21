package cap4j.session;

import cap4j.Variables;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class SessionContext {
    Variables variables;

    protected SessionContext(Variables variables) {
        this.variables = variables;
    }
}
