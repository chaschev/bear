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

package bear.context;

import bear.main.phaser.SettableFuture;
import bear.session.DynamicVariable;
import bear.session.Variables;
import chaschev.lang.OpenBean;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import static bear.context.Fun.UNDEFINED;

public class VariablesLayer extends HavingContext<Variables, AbstractContext> {
    private static final Logger logger = LoggerFactory.getLogger(VariablesLayer.class);

    String name;
    VariablesLayer fallbackVariablesLayer;

    protected ConcurrentHashMap<Object, Object> constants = new ConcurrentHashMap<Object, Object>();
    protected ConcurrentHashMap<String, DynamicVariable> variables = new ConcurrentHashMap<String, DynamicVariable>();

    public VariablesLayer(String name, VariablesLayer fallbackVariablesLayer) {
        super(null);

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

    public VariablesLayer put(String key, DynamicVariable value) {
        putUnlessFrozen(key, value);

        return this;
    }

    public VariablesLayer putConstObj(Object key, Object value) {
        logger.debug("{}: {} <- {}", name, key, value);
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

    public <T> VariablesLayer putConst(DynamicVariable<T> key, T value) {
        return putConst(key.name(), value);
    }

    public VariablesLayer putConst(Nameable key, Object value) {
        return putConst(key.name(), value);
    }

//    public VariablesLayer putConst(String value, Object obj) {
//        System.out.println();
//        return this;
//    }

    public VariablesLayer removeConst(Nameable key) {
        return removeConst(key.name());
    }

    public VariablesLayer removeConst(String name) {
        Object remove = constants.remove(name);
        if(remove != null){
            logger.debug("{}: removed :{} ({})",this.name, name, remove);
        }
        return this;
    }

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
        return (T) getByVarName(null, varName, _default, this, false);
    }

    public <T> T get(DynamicVariable<T> var) {
        return (T) getNoTemplates(var, UNDEFINED);
    }

    private <T> T atomicMemoize(DynamicVariable<?> var, String varName, Object _default) {
        SettableFuture<T> future = new SettableFuture<T>();
        Object o = constants.putIfAbsent(var.name(), future);
        boolean iAmTheOwner = o == null;
        if(iAmTheOwner){
            try {
                T result = (T) getByVarName(var, varName, _default, this, true);
                future.set(result);
                return result;
            } catch (Exception e) {
                future.setException(e);
                throw Exceptions.runtime(e);
            }

        }else{
            try {
                //todo define memoization timeout
                return ((Future<T>)o).get();
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }
        }

//            throw new IllegalStateException("can't be here");
    }

    public Object getConstant(Object obj) {
        return getConstant(obj, false);
    }

    public Object getConstant(Object obj, boolean memoization) {
        if(obj == DynamicVariable.TEMP_VAR) return null;

        Object o = constants.get(obj);

        if(o instanceof Future){
            if(memoization){
                return null;
            }

            try {
                //todo define memoization timeout
                return ((Future)o).get();
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }
        }

        return o;
    }

    public <T> T get(DynamicVariable<T> var, T _default) {
        return (T) getNoTemplates(var, _default);
    }

    protected Object getNoTemplates(DynamicVariable<?> var, Object _default) {
        String varName = var.name();

        return getByVarName(var, varName, _default, this, false);
    }

    /**
     *
     *
     * @param var null, if not
     * @param varName
     * @param _default
     * @param initialLayer
     * @param memoization
     * @return
     */
    protected Object getByVarName(
        @Nullable DynamicVariable<?> var, @Nullable String varName, Object _default,
        VariablesLayer initialLayer, boolean memoization) {
        Preconditions.checkArgument(var != null || varName != null, "they can't both be null!");
        Preconditions.checkArgument(var == null || var.isNameSet(), "var must have name set");


        if(!memoization && var != null && var.memoizeIn() == $.getClass()){
            return atomicMemoize(var, varName, _default);
        }

//        if(this == initialLayer){
//            logger.debug("{}: evaluating :{}...", name, varName);
//        }

        final Object thisLayerResult;

        //first check if var was overridden in this layer
        // don't check temp vars for constants
        if(var == null || !var.isTemporal()){
            Object o = varName == null ? null : getConstant(varName, memoization);

            if(o != null){
                logger.debug("{}: :{} -> {} (const)", name, varName, o);
                return o;
            }
        }

        DynamicVariable<?> r = varName == null ? null : variables.get(varName);

        if (r == null) {
            //not overridden, fall back
            if (fallbackVariablesLayer != null) {
                logger.debug("{}: :{}, falling back to {}", name, varName, fallbackVariablesLayer.name);

                return fallbackVariablesLayer.getByVarName(var, varName, UNDEFINED, initialLayer, memoization);
            }else{
                // there is no fallback, we are in a global scope,
                // apply variable in the initial context

                // if var is not provided, try to get it's function from register
                AppGlobalContext global = $.getGlobal();

                VariableInfo info = global == null ? null : global.variableRegistry.get(varName);

                if(info != null){
                    var = info.var;
                }

                if (var != null) {
                    Object temp;

                    try {
                        temp = var.apply(initialLayer.$);
                    } catch (Exception e) {
                        throw Exceptions.runtime(e);
                    }

                    thisLayerResult = chooseDefined(temp, _default);
                    logger.debug("{}: :{} -> {} (var.apply)", name, varName, thisLayerResult);
                } else {
                    thisLayerResult = UNDEFINED;
                    logger.debug("{}: :{} -> {} (global undef)", name, varName, thisLayerResult);
                }
            }

        } else {
            //overridden in the variables layer

            thisLayerResult = chooseDefined(r.apply($), _default);
            logger.debug("{}: :{} <- {} (overridden var)", name, varName, thisLayerResult);
        }

        if(thisLayerResult == UNDEFINED){
            throw new Fun.UndefinedException(":"+varName+ " is not defined");
        }

        return thisLayerResult;
    }

    private static Object chooseDefined(Object x, Object _default) {
        Object temp  = x;

        if(temp == UNDEFINED) {
            temp = _default;
        }
        return temp;
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
        final VariablesLayer v = new VariablesLayer("dup of " + name, fallbackVariablesLayer);

        v.set$($);

        v.variables = new ConcurrentHashMap<String, DynamicVariable>(variables);

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

        String scope = scopeClass == null ? "" : DependencyInjection.shorten(scopeClass.getSimpleName()) + ".";

        for (Field field : fields) {
            Var varAnnotation = field.getAnnotation(Var.class);

            Class<?> fieldClass = field.getType();

            if (varAnnotation != null) {

                boolean autoImplThis = varAnnotation.autoWireImpl();
//                    && !(CharSequence.class.isAssignableFrom(fieldClass)) &&
//                    !ClassUtils.isPrimitiveOrWrapper(fieldClass);

                if (autoImplThis) {
                    Preconditions.checkArgument("".equals(varAnnotation.constant()), "value & auto-impl for field " + field);
                }

                if (autoImplThis || "".equals(varAnnotation.constant())) {
                    Object closestClass = findClosestClass(fieldClass, layer.constants.keySet(), field, layer);

                    if (closestClass == null) {
                        throw new RuntimeException("could not wire impl for " + field);
                    }

                    Object o = layer.getConstant(closestClass);

                    setField(field, object, o);

                    continue;
                }

                setField(field, object, layer, concatBlank(scope, varAnnotation.constant()));

                continue;
            }

            if (autowire) {
                String varName = concatBlank(scope, field.getName());
                DynamicVariable<Object> variable = layer.getVariable(varName);

                if (variable != null) {
                    setField(field, object, layer, variable.name());

                    continue;
                }

                Object o = layer.get(varName, UNDEFINED);

                if (o != UNDEFINED) {
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
        Object value = layer.get(varName, UNDEFINED);

        if (value != UNDEFINED) {
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

    public <T> boolean isConstantDefined(Nameable<T> variable) {
        return constants.containsKey(variable.name());
    }

    public <T> boolean isSet(Nameable<T> variable) {
        if(isConstantDefined(variable)){
            return true;
        }

        final DynamicVariable<T> x = getVariable(variable);

        return x != null && x.isSet();
    }

    public VariablesLayer putMap(Map<?, ?> map) {
        putMap(map, false);
        return this;
    }

    public Map putMap(Map<?, ?> map, final boolean returnOldValues) {
        Map oldValues = returnOldValues ? new HashMap(map.size()) : null;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();

            String stringKey;

            if (key instanceof Nameable) {
                stringKey = ((Nameable) key).name();
            }else{
                stringKey = (String) key;
            }

            if (value instanceof DynamicVariable) {
                DynamicVariable variable = (DynamicVariable) value;


                DynamicVariable oldValue = variables.put(stringKey, variable);
                if(returnOldValues){
                    oldValues.put(stringKey, oldValue);
                }
            }else{
                Object oldValue;
                if(value == null){
                    oldValue = constants.remove(stringKey);
                }else{
                    oldValue = constants.put(stringKey, value);
                }
                if(returnOldValues){
                    oldValues.put(stringKey, oldValue);
                }
            }
        }

        return oldValues;
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("VariablesLayer{");
        sb.append("name='").append(name).append('\'');
        sb.append(", fallbackVariablesLayer=").append(fallbackVariablesLayer.getName());
        sb.append(", constants=").append(constants.size());
        sb.append(", variables=").append(variables.size());
        sb.append('}');
        return sb.toString();
    }
}