package bear.session;

import bear.core.SessionContext;
import bear.core.VarFun;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.Arrays;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearVariables {
    public static DynamicVariable<String> joinPath(final DynamicVariable<String> root, final String... folders) {
        return joinPath(null, root, folders);
    }

    public static DynamicVariable<String> joinPath(String name, final DynamicVariable<String> root, final String... folders) {
        return Variables.strVar("").setDynamic(new VarFun<String, SessionContext>() {
            public String apply(SessionContext $) {
                return $.sys.joinPath($.var(root), $.joinPath(folders));
            }
        });
    }

    public static DynamicVariable<String> joinPath(final DynamicVariable... folders) {
        return joinPath(null, folders);
    }

    public static DynamicVariable<String> joinPath(String name, final DynamicVariable... folders) {
        return Variables.strVar("").setDynamic(new VarFun<String, SessionContext>() {
            public String apply(final SessionContext $) {
                return $.sys.joinPath(Iterables.transform(Arrays.asList(folders), new Function<DynamicVariable, String>() {
                    public String apply(DynamicVariable var) {
                        return $.var((DynamicVariable<String>) var);
                    }
                }));
            }
        });
    }
}
