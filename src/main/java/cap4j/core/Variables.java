package cap4j.core;

import cap4j.session.DynamicVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;

import static cap4j.core.CapConstants.bool;

public class Variables {
    private static final Logger logger = LoggerFactory.getLogger(Variables.class);


    String name;
    private final Variables fallbackVariables;

    public LinkedHashMap<String, DynamicVariable> variables = new LinkedHashMap<String, DynamicVariable>();

    public Variables(String name, Variables fallbackVariables) {
        this.name = name;
        this.fallbackVariables = fallbackVariables;
    }

    public void set(Nameable name, String value) {
        putUnlessFrozen(name, new DynamicVariable<String>(name, "").defaultTo(value));
    }

    private void putUnlessFrozen(Nameable name, DynamicVariable val) {
        final DynamicVariable variable = variables.get(name.name());

        if(variable == null){
            variables.put(name.name(), val);
        }else
        if(!variable.isFrozen()){
            variables.put(name.name(), val);
        }else{
            throw new IllegalStateException("can't assign to " + name + ": it is frozen");
        }
    }

    public Variables put(Nameable key, DynamicVariable value) {
        putUnlessFrozen(key, value);
        return this;
    }

    public Variables putS(Nameable key, String value) {
        put(key, CapConstants.strVar(key.name(), "external var").defaultTo(value));
        return this;
    }

    public Variables putB(Nameable key, boolean b) {
        put(key, bool(key.name(), "external var").defaultTo(b));
        return this;
    }

    public String getString(DynamicVariable name, String _default) {
        final Object result = get(name, _default);

        if (result == null) return null;

        return result.toString();
    }

    public <T> T get(DynamicVariable<T> name, T _default) {
        return get(new SessionContext(this), name, _default);
    }

    public <T> T get(SessionContext context, Nameable<T> name, T _default) {
        final Object result;

        final DynamicVariable r = variables.get(name.name());

        if (r == null) {
            result = _default;
        } else {
            result = r.apply(context);
        }

        logger.debug(":{} -> {} (by name)", name.name(), result);

        return (T) result;
    }

    public <T> T get(SessionContext context, DynamicVariable<T> var) {
        return get(context, var, (T)null);
    }

    public <T> T get(SessionContext context, DynamicVariable<T> var, T _default) {
        final T result;

        DynamicVariable<T> r = variables.get(var.name());

        if (r == null && fallbackVariables != null) {
            r = fallbackVariables.getClosure(var);
        }

        if(r == null){
            T temp;

            try{
                temp = var.apply(context);
            }catch (Exception e){
                temp = null;
            }

            if(temp == null){
                result = _default;
            }else {
                result = temp;
            }
        }else{
            result = r.apply(context);
        }

        logger.debug(":{} -> {}", var.name(), result);

        return result;
    }

    public <T> DynamicVariable<T> getClosure(Nameable<T> name) {
        DynamicVariable var = variables.get(name.name());

        if(var  == null && fallbackVariables != null){
            var = fallbackVariables.getClosure(name);
        }

        return var;
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
        final Variables v = new Variables("dup of " + name, fallbackVariables);

        v.variables = new LinkedHashMap<String, DynamicVariable>(variables);

        return v;
    }


}