package cap4j.session;

import cap4j.GlobalContext;
import cap4j.Variables;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class SessionContextFactory {
    public SessionContext create(final GlobalContext globalContext){
        Variables variables =
            new Variables(globalContext)
                .fallbackTo(globalContext.variables, "deployTo", "deployVia");

        return new SessionContext(variables) {
        };
    }

}
