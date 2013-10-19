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

public class VariablesLayer extends HavingContext<Variables, AbstractContext> {
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

    private void putUnlessFrozen(String varName, DynamicVariable val) {
        if (isFrozen(varName)) {
            throwFrozen(varName);
        } else {
            variables.put(varName, val);
        }
    }

    private static void throwFrozen(String varName) {
        throw new IllegalStateException("can't assign to " + varName + ": it is frozen");
    }

    public boolean isFrozen(String varName) {
        final DynamicVariable variable = variables.get(varName);
        return isFrozen(variable);
    }

    public static boolean isFrozen(DynamicVariable variable) {
        return variable != null && variable.isFrozen();
    }

    private VariablesLayer putUnlessFrozen(Nameable name, DynamicVariable val) {
        String key = name.name();

        putUnlessFrozen(key, val);

        return this;
    }

    public VariablesLayer put(Nameable key, DynamicVariable value) {
        return putUnlessFrozen(key, value);
    }

    public VariablesLayer putConstObj(Object key, Object value) {
        Preconditions.checkArgument(!(key instanceof DynamicVariable || value instanceof DynamicVariable), "dynamic variables are not constants!");
        constants.put(key, value);
        return this;
    }

    public VariablesLayer put(DynamicVariable value) {
        return put(value, value);
    }

    public VariablesLayer put(Nameable key, String value) {
        return putConst(key.name(), value);
    }

    public VariablesLayer putConst(Nameable key, Object value) {
        return putConst(key.name(), value);
    }

//    public VariablesLayer putConst(String value, Object obj) {
//        System.out.println();
//        return this;
//    }

    public VariablesLayer putConst(String name, Object value) {
        DynamicVariable variable = variables.get(name);

        if(isFrozen(variable)){
            throwFrozen(name);
        }

        if(variable != null){
            variables.remove(name);
        }

        putConstObj(name, value);
        return this;
    }

    public VariablesLayer putB(Nameable key, boolean b) {
        putConst(key, b);
        return this;
    }

    public String getString(DynamicVariable name, String _default) {
        final Object result = get(name, _default);

        if (result == null) return null;

        return result.toString();
    }

    public <T> T get(Nameable<T> name, T _default) {
        return get(name.name(), _default);
    }

    public <T> T get(String varName, T _default) {
        final DynamicVariable r = getVariable(varName);
        final Object result;

        if (r == null) {
            Object o = constants.get(varName);

            result = elvis(o, _default);
        } else {
            result = r.apply($);
        }

        logger.debug(":{} -> {} (by name)", varName, result);

        return (T) result;
    }

    public <T> T get(DynamicVariable<T> var) {
        return get(var, (T) null);
    }

    public Object getConstant(Object obj) {
        return constants.get(obj);
    }

    public <T> T get(DynamicVariable<T> var, T _default) {
        final T result;

        DynamicVariable<T> r = variables.get(var.name());

        if (r == null && fallbackVariablesLayer != null) {
            r = fallbackVariablesLayer.getVariable(var);
        }

        if (r == null) {
            T temp;

            try {
                temp = var.apply($);
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }

            if (temp == null) {
                result = _default;
            } else {
                result = temp;
            }
        } else {
            result = r.apply($);
        }

        logger.debug(":{} -> {}", var.name(), result);

        return result;
    }

    public <T> DynamicVariable<T> getVariable(Nameable<T> name) {
        return getVariable(name.name());
    }

    public <T> DynamicVariable<T> getVariable(String key) {
        DynamicVariable var = variables.get(key);

        if (var == null && fallbackVariablesLayer != null) {
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

    public VariablesLayer dup() {
        final VariablesLayer v = new VariablesLayer($, "dup of " + name, fallbackVariablesLayer);

        v.variables = new LinkedHashMap<String, DynamicVariable>(variables);

        return v;
    }

    public <T> T wire(T object) {
        wire(this, object);

        return object;
    }

    public static void wire(VariablesLayer layer, Object object) {
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

            if (varAnnotation != null) {

                boolean autoImplThis = varAnnotation.autoWireImpl();
//                    && !(CharSequence.class.isAssignableFrom(fieldClass)) &&
//                    !ClassUtils.isPrimitiveOrWrapper(fieldClass);

                if (autoImplThis) {
                    Preconditions.checkArgument("".equals(varAnnotation.value()), "value & auto-impl for field " + field);
                }

                if (autoImplThis || "".equals(varAnnotation.value())) {
                    Object closestClass = findClosestClass(fieldClass, layer.constants.keySet(), field, layer);

                    if (closestClass == null) {
                        throw new RuntimeException("could not wire impl for " + field);
                    }

                    Object o = layer.getConstant(closestClass);

                    setField(field, object, o);

                    continue;
                }

                setField(field, object, layer, concatBlank(scope, varAnnotation.value()));

                continue;
            }

            if (autowire) {
                String varName = concatBlank(scope, field.getName());
                DynamicVariable<Object> variable = layer.getVariable(varName);

                if (variable != null) {
                    setField(field, object, layer, variable.name);

                    continue;
                }

                Object o = layer.get(varName, Void.class);

                if (o != Void.class) {
                    setField(field, object, layer, varName);

                    continue;
                }

                Object closestClass = null;

                try {
                    closestClass = findClosestClass(fieldClass, layer.constants.keySet(), field, layer);
                } catch (MultipleDICandidates ignore) {

                }

                if (closestClass != null) {
                    setField(field, object, layer.getConstant(closestClass));
                }
            }
        }
    }

    private static void setField(Field field, Object object, VariablesLayer layer, String varName) {
        Object value = layer.get(varName, Void.class);

        if (value != Void.class) {
            setField(field, object, value);
        }
    }

    private static void setField(Field field, Object object, Object value) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("wiring {}.{} to {}", field.getDeclaringClass().getSimpleName(), field.getName(), value);
            }

            field.set(object, value);
        } catch (IllegalArgumentException e) {
            throw Exceptions.runtime(e);
        } catch (IllegalAccessException e) {
            throw Exceptions.runtime(e);
        }
    }

    private static String concatBlank(String s1, String s2) {
        return "".equals(s1) ? s2 : s1 + s2;
    }

    //todo: respect the annotations, rename to reverse wire
    public VariablesLayer addVariables(Object varsObject) {
        try {
            for (Field field : OpenBean.fieldsOfType(varsObject.getClass(), DynamicVariable.class, true)) {
                this.put((DynamicVariable) field.get(varsObject));
            }

            return this;
        } catch (IllegalAccessException e) {
            throw Exceptions.runtime(e);
        }
    }

    private static class MultipleDICandidates extends RuntimeException {

        private MultipleDICandidates(String message) {
            super(message);
        }
    }

    private static Class<?> findClosestClass(Class<?> fieldClass, Iterable<Object> objects, Field field, VariablesLayer layer) {
        Object o = layer.getConstant(fieldClass);

        if (o != null) {
            return fieldClass;
        }

        Class<?> closestClass = null;

        for (Object key : objects) {
            if (!(key instanceof Class)) {
                continue;
            }
            Class key1 = (Class) key;

            if (!fieldClass.isAssignableFrom(key1)) continue;

            if (closestClass == null) {
                closestClass = key1;
            } else if (closestClass.isAssignableFrom(key1)) {
                closestClass = key1;
            } else if (!key1.isAssignableFrom(closestClass)) {
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