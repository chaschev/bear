package cap4j;

import cap4j.session.SessionContext;
import cap4j.session.SystemEnvironment;
import cap4j.session.Variable;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;

public class Variables {
    private final GlobalContext globalContext;

    public LinkedHashMap<Nameable, Variable> variables = new LinkedHashMap<Nameable, Variable>();

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

    public Variables(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    public void set(Nameable name, String value) {
        putUnlessFrozen(name, new GlobalContext.ConstantString(value));
    }

    private Variable putUnlessFrozen(Nameable name, Variable val) {
        final Variable variable = variables.get(name);

        boolean frozen = variable.isFrozen();

        if(!frozen){
            return variables.put(name, val);
        }else{
            throw new IllegalStateException("can't assign to " + name + ": it is frozen");
        }
    }

    public Variables put(Nameable key, Variable value) {
        putUnlessFrozen(key, value);
        return this;
    }

    public String getString(SessionContext context, Nameable name, String _default) {
        final Object result = get(context, name, _default);

        if (result == null) return null;

        return result.toString();
    }

    public String getString(Nameable name, String _default) {
        final Object result = get(name, _default);

        if (result == null) return null;

        return result.toString();
    }

    public <T> T get(SessionContext context, Nameable name, T _default) {
        return get(new Context(globalContext, context, null), name, _default);
    }

    public <T> T get(Nameable name, T _default) {
        return get(new Context(globalContext, null, null), name, _default);
    }

    public <T> T get(Context context, Nameable name, T _default) {
        final Object result;

        final Variable r = getClosure(name);

        if (r == null) {
            result = _default;
        } else {
            result = r.apply(context);
        }

        return (T) result;
    }

    public Variable getClosure(Nameable name) {
        return variables.get(name.name());
    }

    public Variables fallbackTo(final Variables srcVariables, Nameable... names){
        for (Nameable name : names) {
            put(name, fallback(name, srcVariables));
        }

        return this;
    }

    public static Variable fallback(final Nameable name2, final Variables srcVariables) {
        return new Variable() {
            public Object apply(@Nullable Context input) {
                return srcVariables.getClosure(name2);
            }
        };
    }

    public Variables dup(){
        final Variables v = new Variables(globalContext);

        v.variables = new LinkedHashMap<Nameable, Variable>(variables);

        return v;
    }

    public void freeze(VariableName variable){
        getClosure(variable).setFrozen(true);
    }

}