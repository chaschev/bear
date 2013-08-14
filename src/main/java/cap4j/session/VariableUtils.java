package cap4j.session;

import cap4j.core.SessionContext;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.Arrays;

import static cap4j.core.CapConstants.bool;
import static cap4j.core.CapConstants.dynamic;
import static cap4j.core.CapConstants.strVar;

/**
 * User: chaschev
 * Date: 7/31/13
 */
public class VariableUtils {
    public static DynamicVariable<String> joinPath(final DynamicVariable<String> root, final String... folders){
        return joinPath(null, root, folders);
    }


    public static DynamicVariable<String> joinPath(String name, final DynamicVariable<String> root, final String... folders){
        return strVar(name, "").setDynamic(new Function<SessionContext, String>() {
            public String apply(SessionContext ctx) {
                return ctx.system.joinPath(ctx.var(root), ctx.joinPath(folders));
            }
        });
    }

    public static DynamicVariable<String> joinPath(final DynamicVariable... folders){
        return joinPath(null, folders);
    }

    public static DynamicVariable<String> joinPath(String name,final DynamicVariable... folders){
        return strVar(name, "").setDynamic(new Function<SessionContext, String>() {
            public String apply(final SessionContext ctx) {
                return ctx.system.joinPath(Iterables.transform(Arrays.asList(folders), new Function<DynamicVariable, String>() {
                    public String apply(DynamicVariable var) {
                        return ctx.var((DynamicVariable<String>)var);
                    }
                }));
            }
        });
    }

    public static DynamicVariable<Boolean> not(String name, final DynamicVariable<Boolean> b){
        return bool(name, "").setDynamic(new Function<SessionContext, Boolean>() {
            public Boolean apply(final SessionContext ctx) {
                return !ctx.varB(b);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> eql(String name, final DynamicVariable<T> var, final String to){
        return dynamic(name, "", new Function<SessionContext, Boolean>() {
            public Boolean apply(final SessionContext ctx) {
                final T v = ctx.var(var);
                return v == null ? to == v : String.valueOf(v).equals(to);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> isSet(final DynamicVariable<T> var){
        return isSet(null, var);
    }

    public static <T> DynamicVariable<Boolean> isSet(String name, final DynamicVariable<T> var){
        return dynamic(name, "", new Function<SessionContext, Boolean>() {
            public Boolean apply(final SessionContext ctx) {
                final DynamicVariable<T> x = ctx.sessionVariables.getClosure(var);

                return x != null && x.isSet();

            }
        });
    }

    public static <T> DynamicVariable<T> condition(final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar){
        return condition(condition, trueVar, falseVar);
    }

    public static <T> DynamicVariable<T> condition(String name, final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar){
        return dynamic(name, "", new Function<SessionContext, T>() {
            public T apply(final SessionContext ctx) {
                return ctx.varB(condition) ? ctx.var(trueVar) : ctx.var(falseVar);
            }
        });
    }

    public static <T> DynamicVariable<T> eql(String name, final DynamicVariable<T> var){
        return dynamic(name, "", new Function<SessionContext, T>() {
            public T apply(final SessionContext ctx) {
                return ctx.var(var);
            }
        });
    }

    public static DynamicVariable<Boolean> and(String name, final DynamicVariable... bools){
        return bool(name, "").setDynamic(new Function<SessionContext, Boolean>() {
            public Boolean apply(final SessionContext ctx) {
                for (DynamicVariable b : bools) {
                    if(!ctx.varB(b)) return false;
                }

                return true;
            }
        });
    }

    public static DynamicVariable<Boolean> or(String name, final DynamicVariable... bools){
        return bool(name, "").setDynamic(new Function<SessionContext, Boolean>() {
            public Boolean apply(final SessionContext ctx) {
                for (DynamicVariable b : bools) {
                    if(ctx.varB(b)) return true;
                }

                return false;
            }
        });
    }


}
