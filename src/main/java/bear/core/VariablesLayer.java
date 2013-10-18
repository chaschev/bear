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

import bear.plugins.AbstractContext;
import bear.plugins.HavingContext;
import bear.session.DynamicVariable;
import bear.session.Variables;
import chaschev.lang.OpenBean;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;

import static chaschev.lang.LangUtils.elvis;

public class VariablesLayer extends HavingContext<Variables, AbstractContext>{
    private static final Logger logger = LoggerFactory.getLogger(VariablesLayer.class);

    String name;
    private final VariablesLayer fallbackVariablesLayer;

    protected LinkedHashMap<Object, Object> constants = new LinkedHashMap<Object, Object>();
    protected LinkedHashMap<String, DynamicVariable> variables = new LinkedHashMap<String, DynamicVariable>();

    public VariablesLayer(AbstractContext $, String name, VariablesLayer fallbackVariablesLayer) {
        super($);

        this.name = name;
        this.fallbackVariablesLayer = fallbackVariablesLayer;
    }

    public void set(Nameable name, String value) {
        putUnlessFrozen(name, new DynamicVariable<String>(name, "").defaultTo(value));
    }

    private void putUnlessFrozen(String key, DynamicVariable val) {
        final DynamicVariable variable = variables.get(key);

        if(variable == null){
            variables.put(key, val);
        }else
        if(!variable.isFrozen()){
            variables.put(key, val);
        }else{
            throw new IllegalStateException("can't assign to " + name + ": it is frozen");
        }
    }

    private VariablesLayer putUnlessFrozen(Nameable name, DynamicVariable val) {
        String key = name.name();

        putUnlessFrozen(key, val);

        return this;
    }

    public VariablesLayer put(Nameable key, DynamicVariable value) {
        return putUnlessFrozen(key, value);
    }

    public VariablesLayer putConst(Object key, Object value) {
        constants.put(key, value);
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

    public <T> T get(Nameable<T> name, T _default) {
        final Object result;

        final DynamicVariable r = variables.get(name.name());

        if (r == null) {
            Object o = constants.get(name.name());

            result = elvis(o, _default);
        } else {
            result = r.apply($);
        }

        logger.debug(":{} -> {} (by name)", name.name(), result);

        return (T) result;
    }

    public <T> T get(DynamicVariable<T> var) {
        return get(var, (T)null);
    }

    public Object get(Object obj) {
        return constants.get(obj);
    }

    public <T> T get(DynamicVariable<T> var, T _default) {
        final T result;

        DynamicVariable<T> r = variables.get(var.name());

        if (r == null && fallbackVariablesLayer != null) {
            r = fallbackVariablesLayer.getVariable(var);
        }

        if(r == null){
            T temp;

            try{
                temp = var.apply($);
            }catch (Exception e){
                throw Exceptions.runtime(e);
            }

            if(temp == null){
                result = _default;
            }else {
                result = temp;
            }
        }else{
            result = r.apply($);
        }

        logger.debug(":{} -> {}", var.name(), result);

        return result;
    }

    public <T> DynamicVariable<T> getVariable(Nameable<T> name) {
        return getVariable(name.name());
    }

    public  <T> DynamicVariable<T> getVariable(String key) {
        DynamicVariable var = variables.get(key);

        if(var  == null && fallbackVariablesLayer != null){
            var = fallbackVariablesLayer.getVariable(key);
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
        final VariablesLayer v = new VariablesLayer($, "dup of " + name, fallbackVariablesLayer);

        v.variables = new LinkedHashMap<String, DynamicVariable>(variables);

        return v;
    }

    public <T> T wire(T object){
        wire(this, object);

        return object;
    }

    public static void wire(VariablesLayer layer, Object object){
        Class<?> aClass = object.getClass();

        boolean autowire = true;
        Class scopeClass = null;

        {
            final WireFields a = aClass.getAnnotation(WireFields.class);

            if (a != null) {
                autowire = a.autowire();
                scopeClass = a.value() == Void.class ? null : a.value();
            }
        }

        Field[] fields = OpenBean.getClassDesc(aClass).fields;

        String scope = scopeClass == null ? "" : scopeClass.getSimpleName() + ".";

        for (Field field : fields) {
            Var varAnnotation = field.getAnnotation(Var.class);

            Class<?> fieldClass = field.getType();

            if(varAnnotation != null){
                Preconditions.checkArgument(varAnnotation.autoWireImpl() && "".equals(varAnnotation.value()), "value & auto-impl");

                if(varAnnotation.autoWireImpl() || "".equals(varAnnotation.value())){
                    Object o = layer.get(fieldClass);

                    if(o == null){
                        Object closestClass = findClosestClass(fieldClass, layer.constants.keySet(), field);

                        if(closestClass == null){
                            throw new RuntimeException("could not wire impl for " + field);
                        }

                        o = layer.get(closestClass);
                    }


                    setField(field, object, o);

                    continue;
                }


                setField(field, object, layer.get(concatBlank(scope, varAnnotation.value())));

                continue;
            }

            if(autowire){
                DynamicVariable<Object> variable = layer.getVariable(scope + field.getName());

                if(variable != null){
                    setField(field, object, layer.get(variable));

                    continue;
                }

                Object closestClass = null;

                try {
                    closestClass = findClosestClass(fieldClass, layer.constants.keySet(), field);
                } catch (MultipleDICandidates ignore) {

                }

                if(closestClass != null){
                    setField(field, object, layer.get(closestClass));
                }
            }
        }
    }

    private static void setField(Field field, Object object, Object value) {
        if(logger.isDebugEnabled()){
            logger.debug("wiring {}.{} to {}", field.getDeclaringClass().getSimpleName(), field.getName(), value);
        }

        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw Exceptions.runtime(e);
        }
    }

    private static String concatBlank(String s1, String s2) {
        return "".equals(s1) ? s2 : s1 + s2;
    }

    private static class MultipleDICandidates extends RuntimeException{

        private MultipleDICandidates(String message) {
            super(message);
        }
    }
    private static Class<?> findClosestClass(Class<?> fieldClass, Iterable<Object> objects, Field field) {
        Class<?> closestClass = null;

        for (Object key : objects) {
            if (!(key instanceof Class)) {
                continue;
            }
            Class key1 = (Class) key;

            if (!fieldClass.isAssignableFrom(key1)) continue;

            if(closestClass == null){
                closestClass = key1;
            }else
            if(closestClass.isAssignableFrom(key1)){
                closestClass = key1;
            }else
            if(!key1.isAssignableFrom(closestClass)){
                throw new MultipleDICandidates("two types possible to inject to field " + field + ": " + key1.getSimpleName() + " and " + closestClass);
            }
        }
        return closestClass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}