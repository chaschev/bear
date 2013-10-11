/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.core;

import bear.session.DynamicVariable;
import bear.session.Variables;
import chaschev.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;

public class VariablesLayer {
    private static final Logger logger = LoggerFactory.getLogger(VariablesLayer.class);

    String name;
    private final VariablesLayer fallbackVariablesLayer;

    public LinkedHashMap<String, DynamicVariable> variables = new LinkedHashMap<String, DynamicVariable>();

    public VariablesLayer(String name, VariablesLayer fallbackVariablesLayer) {
        this.name = name;
        this.fallbackVariablesLayer = fallbackVariablesLayer;
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

    public VariablesLayer put(Nameable key, DynamicVariable value) {
        putUnlessFrozen(key, value);
        return this;
    }

    public VariablesLayer put(DynamicVariable value) {
        return put(value, value);
    }

    public VariablesLayer putS(Nameable key, String value) {
        put(key, Variables.strVar("external var").defaultTo(value));
        return this;
    }

    public VariablesLayer putB(Nameable key, boolean b) {
        put(key, Variables.bool("external var").defaultTo(b));
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

        if (r == null && fallbackVariablesLayer != null) {
            r = fallbackVariablesLayer.getClosure(var);
        }

        if(r == null){
            T temp;

            try{
                temp = var.apply(context);
            }catch (Exception e){
                throw Exceptions.runtime(e);
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

        if(var  == null && fallbackVariablesLayer != null){
            var = fallbackVariablesLayer.getClosure(name);
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

    public VariablesLayer dup(){
        final VariablesLayer v = new VariablesLayer("dup of " + name, fallbackVariablesLayer);

        v.variables = new LinkedHashMap<String, DynamicVariable>(variables);

        return v;
    }


}