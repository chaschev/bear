package cap4j.session;

import cap4j.core.SessionContext;
import cap4j.core.VarFun;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.Arrays;

import static cap4j.core.CapConstants.*;

/**
 * User: chaschev
 * Date: 7/31/13
 */
public class VariableUtils {
    public static DynamicVariable<String> joinPath(final DynamicVariable<String> root, final String... folders){
        return joinPath(null, root, folders);
    }


    public static DynamicVariable<String> joinPath(String name, final DynamicVariable<String> root, final String... folders){
        return strVar(name, "").setDynamic(new VarFun<String>() {
            public String apply() {
                return ctx.system.joinPath(ctx.var(root), ctx.joinPath(folders));
            }
        });
    }

    public static DynamicVariable<String> joinPath(final DynamicVariable... folders){
        return joinPath(null, folders);
    }

    public static DynamicVariable<String> joinPath(String name,final DynamicVariable... folders){
        return strVar(name, "").setDynamic(new VarFun<String>() {
            public String apply() {
                return ctx.system.joinPath(Iterables.transform(Arrays.asList(folders), new Function<DynamicVariable, String>() {
                    public String apply(DynamicVariable var) {
                        return ctx.var((DynamicVariable<String>) var);
                    }
                }));
            }
        });
    }

    public static DynamicVariable<Boolean> not(String name, final DynamicVariable<Boolean> b){
        return bool(name, "").setDynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return !ctx.varB(b);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> isEql(String name, final DynamicVariable<T> variable, final String to){
        return dynamic(name, "", new VarFun<Boolean>() {
            public Boolean apply() {
                final T v = ctx.var(variable);
                return v == null ? to == v : String.valueOf(v).equals(to);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> isSet(final DynamicVariable<T> var){
        return isSet(null, var);
    }

    public static <T> DynamicVariable<Boolean> isSet(String name, final DynamicVariable<T> variable){
        return dynamic(name, "", new VarFun<Boolean>() {
            public Boolean apply() {
                final DynamicVariable<T> x = ctx.sessionVariables.getClosure(variable);

                return x != null && x.isSet();

            }
        });
    }

    public static <T> DynamicVariable<T> condition(final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar){
        return condition(null, condition, trueVar, falseVar);
    }

    public static <T> DynamicVariable<T> condition(String name, final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar){
        return dynamic(name, "", new VarFun<T>() {
            public T apply() {
                return ctx.varB(condition) ? ctx.var(trueVar) : ctx.var(falseVar);
            }
        });
    }

    public static <T> DynamicVariable<T> eql(final DynamicVariable<T> var){
        return eql(null, var);
    }

    public static <T> DynamicVariable<T> eql(String name, final DynamicVariable<T> var){
        return dynamic(name, "", new VarFun<T>() {
            public T apply() {
                return ctx.var(var);
            }
        });
    }

    public static DynamicVariable<Boolean> and(String name, final DynamicVariable... bools){
        return bool(name, "").setDynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                for (DynamicVariable b : bools) {
                    if (!ctx.varB(b)) return false;
                }

                return true;
            }
        });
    }

    public static DynamicVariable<Boolean> or(String name, final DynamicVariable... bools){
        return bool(name, "").setDynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                for (DynamicVariable b : bools) {
                    if (ctx.varB(b)) return true;
                }

                return false;
            }
        });
    }

    public static DynamicVariable<String> concat(final Object... varsAndStrings){
        return dynamic(new VarFun<String>() {
            public String apply() {
                return VariableUtils.concat(ctx, varsAndStrings);
            }
        });
    }

    public static String concat(SessionContext ctx, Object... varsAndStrings) {
        StringBuilder sb = new StringBuilder(128);

        for (Object obj : varsAndStrings) {
            if (obj instanceof CharSequence) {
                sb.append(obj);
            }else if (obj instanceof DynamicVariable) {
                DynamicVariable var = (DynamicVariable) obj;
                sb.append(ctx.var(var));
            }else{
                throw new IllegalStateException(obj + " of class " + obj.getClass().getSimpleName() + " is not supported");
            }
        }

        return sb.toString();
    }
}
