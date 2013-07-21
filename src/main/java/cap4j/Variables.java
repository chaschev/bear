package cap4j;

import cap4j.session.SessionContext;
import cap4j.session.SystemEnvironment;
import cap4j.session.Variable;
import com.google.common.base.Function;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;

public class Variables {
    private final GlobalContext globalContext;

    public static Variable fallback(final String name, final Variables srcVariables) {
        return new Variable() {
            public Object apply(@Nullable Context input) {
                return srcVariables.getClosure(name);
            }
        };
    }



    public static class Context{
        public final GlobalContext globalContext;
        public final SessionContext sessionContext;
        public final SystemEnvironment system;

        public Context(GlobalContext globalContext, SessionContext sessionContext, SystemEnvironment system) {
            this.globalContext = globalContext;
            this.sessionContext = sessionContext;
            this.system = system;
        }
    }

    public LinkedHashMap<String, Variable> variables = new LinkedHashMap<String, Variable>();

    public Variables(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    public void set(String name, String value) {
        variables.put(name, new GlobalContext.ConstantString(value));
    }

    public Function<Variables.Context, Object> put(String key, Variable value) {
        return variables.put(key, value);
    }

    public Variables put(GlobalContext.VariableName key, Variable value) {
        variables.put(key.name(), value);
        return this;
    }

    public String getString(SessionContext context, String name, String _default) {
        final Object result = get(context, name, _default);

        if (result == null) return null;

        return result.toString();
    }

    public <T> T get(SessionContext context, String name, T _default) {
        return get(new Context(globalContext, context), name, _default);
    }

    public <T> T get(Context context, String name, T _default) {
        final Object result;

        final Variable r = getClosure(name);

        if (r == null) {
            result = _default;
        } else {
            result = r.apply(context);
        }

        return (T) result;
    }

    public Variable getClosure(String name) {
        return variables.get(name);
    }

    public Variable getClosure(GlobalContext.VariableName name) {
        return variables.get(name.name());
    }

    public Variables fallbackTo(final Variables srcVariables, String... names){
        for (String name : names) {
            put(name, fallback(name, srcVariables));
        }

        return this;
    }
}