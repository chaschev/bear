package cap4j;

import cap4j.session.SystemEnvironments;
import cap4j.session.Variable;

import static cap4j.GlobalContext.VariableName.applicationName;
import static cap4j.GlobalContext.VariableName.applicationsPath;
import static cap4j.GlobalContext.VariableName.deployTo;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class GlobalContext {
    public final Variables variables = new Variables(null);

    public static class ConstantString implements Variable {
        String s;

        public ConstantString(String s) {
            this.s = s;
        }
        public Object apply(Variables.Context input) {
            return s;
        }
    }

    SystemEnvironments system;

    public static interface GlobalContextFactory{
        GlobalContext create(SystemEnvironments system);
    }

    public static enum VariableName{
        applicationName,
        applicationsPath,
        deployTo;
    }

    GlobalContextFactory globalContextFactory = new GlobalContextFactory() {
        @Override
        public GlobalContext create(SystemEnvironments system) {
            final GlobalContext globalContext = new GlobalContext() {

            };

            final Variables vars = globalContext.variables;

            vars
                .put(deployTo, system.joinPath(vars.getClosure(applicationsPath), vars.getClosure(applicationName)))


            ;


            return globalContext;
        }
    };
}
