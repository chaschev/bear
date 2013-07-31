package cap4j.session;

import cap4j.VarContext;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.Arrays;

import static cap4j.CapConstants.strVar;

/**
 * User: chaschev
 * Date: 7/31/13
 */
public class VariableUtils {
    public static DynamicVariable<String> joinPath(String name, final DynamicVariable<String> root, final String... folders){
        return strVar(name, "").setDynamic(new Function<VarContext, String>() {
            public String apply(VarContext ctx) {
                return ctx.system.joinPath(ctx.varS(root), ctx.joinPath(folders));
            }
        });
    }

    public static DynamicVariable<String> joinPath(String name,final DynamicVariable... folders){
        return strVar(name, "").setDynamic(new Function<VarContext, String>() {
            public String apply(final VarContext ctx) {
                return ctx.system.joinPath(Iterables.transform(Arrays.asList(folders), new Function<DynamicVariable, String>() {
                    public String apply(DynamicVariable var) {
                        return ctx.varS(var);
                    }
                }));
            }
        });
    }


}
