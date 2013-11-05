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
import chaschev.lang.OpenBean;
import chaschev.lang.reflect.ClassDesc;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;

import static bear.session.Variables.getConverter;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class AbstractContext {
    protected VariablesLayer layer;
    protected AbstractContext global;
    protected Properties properties = new Properties();

    protected AbstractContext() {

    }

    protected AbstractContext(VariablesLayer layer) {
        this.layer = layer;
    }

    protected AbstractContext(AbstractContext parentContext) {
        this(new VariablesLayer(null, null, parentContext.layer));
        layer.set$(this);
    }

    protected AbstractContext(VariablesLayer layer, AbstractContext global) {
        this.layer = layer;
    }

    public <T> T var(Nameable<T> var){
        return var(layer.getVariable(var));
    }

    public <T> T var(DynamicVariable<T> varName) {
        return layer.get(varName);
    }

    public <T> T var(DynamicVariable<T> varName, T _default) {
        return layer.get(varName, _default);
    }

    public boolean varB(DynamicVariable<Boolean> var) {
        return layer.get(var);
    }

    public <T> boolean isSet(Nameable<T> variable){
        final DynamicVariable<T> x = layer.getVariable(variable);

        return x != null && x.isSet();
    }

    /**
     * Should be overridden to specify type.
     */
    public AbstractContext getGlobal() {
        return global;
    }

    public VariablesLayer put(Nameable key, DynamicVariable value) {
        return layer.put(key, value);
    }

    public VariablesLayer put(DynamicVariable value) {
        return layer.put(value);
    }

    public VariablesLayer put(Nameable key, String value) {
        return layer.put(key, value);
    }

    public VariablesLayer put(Nameable key, boolean b) {
        return layer.putB(key, b);
    }

    public VariablesLayer put(Object key, Object value) {
        return layer.putConstObj(key, value);
    }

    public <T> T wire(T object) {
        try {
            Class<? extends AbstractContext> myClass = this.getClass();
            Field[] contextFields = OpenBean.getClassDesc(myClass).fields;

            for (Field field : OpenBean.getClassDesc(object.getClass()).fields) {
                if(myClass == field.getType()){
                    field.set(object, this);
                    continue;
                }

                int i = Arrays.binarySearch(contextFields, field, ClassDesc.FIELD_COMPARATOR);

                if(i>=0){
                    Field contextField = contextFields[i];

                    //don't copy field like layer
                    if(contextField.getDeclaringClass() != AbstractContext.class){
                        field.set(object, contextField.get(this));
                    }
                }
            }

            return layer.wire(object);
        } catch (IllegalAccessException e) {
            throw Exceptions.runtime(e);
        }
    }

    public void setName(String name) {
        layer.setName(name);
    }

    public String getName() {
        return layer.getName();
    }

    public VariablesLayer getLayer() {
        return layer;
    }

    public String getProperty(String s) {
        if(global == null){
            return properties.getProperty(s);
        }

        return global.getProperty(s);
    }

    public void loadProperties(File file) {
        try {
            final FileInputStream fis = new FileInputStream(file);
            loadProperties(fis);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }

    }

    public void loadProperties(InputStream is) throws IOException {
        Preconditions.checkNotNull(is);

        properties.load(is);

        loadProperties(properties);
    }

    //todo support conversion
    public void loadProperties(Properties prop) {
        this.properties = prop;

        final Enumeration<?> enumeration = prop.propertyNames();

        while (enumeration.hasMoreElements()) {
            final String name = (String) enumeration.nextElement();

            final Object v = prop.get(name);

            if (v instanceof Boolean) {
                final DynamicVariable<Boolean> value = Variables.newVar((Boolean) v).setName(name);

                layer.put(value, value);
            } else if (v instanceof String) {
                final DynamicVariable<String> value = Variables.newVar((String) v).setName(name);

                layer.put(value, value);
            } else {
                throw new UnsupportedOperationException("todo: implement for " + v.getClass());
            }
        }
    }

    public Object getConstant(Object obj) {
        return layer.getConstant(obj);
    }

    public <T> T get(DynamicVariable<T> var, T _default) {
        return layer.get(var, _default);
    }

    public <T> DynamicVariable<T> getVariable(Nameable<T> name) {
        return layer.getVariable(name);
    }

    public <T> DynamicVariable<T> getVariable(String key) {
        return layer.getVariable(key);
    }

    public AbstractContext putConst(Nameable key, Object value) {
        layer.putConst(key, value);
        return this;
    }

    public AbstractContext putConst(String name, Object value) {
        layer.putConst(name, value);
        return this;
    }

    public AbstractContext convertAndPutConst(String name, String value, Class<?> type) {
        Function<String,?> converter;

        if(type == null){
            converter = Functions.identity();
        }else{
            converter = getConverter(type);
        }

        Preconditions.checkNotNull(converter, "converter not found for type: " +  (type == null ? null : type.getSimpleName()));

        return putConst(name, converter.apply(value));
    }

    public AbstractContext putConstObj(Object key, Object value) {
        layer.putConstObj(key, value);
        return this;
    }

    public AbstractContext removeConst(Nameable key) {
        layer.removeConst(key);
        return this;
    }

    public AbstractContext removeConst(String name) {
        layer.removeConst(name);
        return this;
    }

    public boolean isGlobal(){
        return global == null;
    }

    public AbstractContext setParent(AbstractContext context){
        layer.fallbackVariablesLayer = context.layer;
        if(global == null){
            global = context;
        }
        return this;
    }
}
