package cap4j;

import cap4j.session.SystemEnvironments;
import com.google.common.base.Preconditions;

import static cap4j.VariableName.applicationName;
import static cap4j.VariableName.applicationsPath;
import static cap4j.VariableName.deployTo;

/**
* User: ACHASCHEV
* Date: 7/23/13
*/
public abstract class GlobalContextFactory {

    public GlobalContext create(SystemEnvironments system){
        GlobalContext globalContext = new GlobalContext() {

        };

        final Variables vars = globalContext.variables;

        vars
            .put(deployTo, system.joinPath(vars.getClosure(applicationsPath), vars.getClosure(applicationName)))
        ;

        configure(globalContext, system);

        Preconditions.checkNotNull(vars.getClosure(applicationName));

        GlobalContext.INSTANCE = globalContext;

        return globalContext;
    }

    protected abstract GlobalContext configure(GlobalContext gc, SystemEnvironments system);
}
