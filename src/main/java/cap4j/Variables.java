package cap4j;

import cap4j.session.DynamicVariable;
import cap4j.session.SessionContext;

import java.util.LinkedHashMap;

public class Variables {
    private final Variables fallbackVariables;

    public LinkedHashMap<Nameable, DynamicVariable> variables = new LinkedHashMap<Nameable, DynamicVariable>();

    public Variables(Variables fallbackVariables) {
        this.fallbackVariables = fallbackVariables;
    }

    public void set(Nameable name, String value) {
        putUnlessFrozen(name, new DynamicVariable<String>(name, "").defaultTo(value));
    }

    private DynamicVariable putUnlessFrozen(Nameable name, DynamicVariable val) {
        final DynamicVariable variable = variables.get(name);

        boolean frozen = variable.isFrozen();

        if(!frozen){
            return variables.put(name, val);
        }else{
            throw new IllegalStateException("can't assign to " + name + ": it is frozen");
        }
    }

    public Variables put(Nameable key, DynamicVariable value) {
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
        return get(new VarContext(context.variables, null), name, _default);
    }

    public <T> T get(Nameable name, T _default) {
        return get(new VarContext(null, null), name, _default);
    }

    public <T> T get(VarContext context, Nameable name, T _default) {
        final Object result;

        final DynamicVariable r = getClosure(name);

        if (r == null) {
            result = _default;
        } else {
            result = r.apply(context);
        }

        return (T) result;
    }

    public DynamicVariable getClosure(Nameable name) {
        return variables.get(name);
    }

//    public Variables fallbackTo(final Variables srcVariables, Nameable... names){
//        for (Nameable name : names) {
//            put(name, fallback(name, srcVariables));
//        }
//
//        return this;
//    }

//    public static DynamicVariable fallback(final Nameable name2, final Variables srcVariables) {
//        return new DynamicVariable(name2, "") {
//            public Object apply(@Nullable VarContext input) {
//                return srcVariables.getClosure(name2);
//            }
//        };
//    }

    public Variables dup(){
        final Variables v = new Variables(fallbackVariables);

        v.variables = new LinkedHashMap<Nameable, DynamicVariable>(variables);

        return v;
    }

    public void freeze(VariableName variable){
        getClosure(variable).setFrozen(true);
    }



}