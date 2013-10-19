package bear.plugins;

import bear.core.Nameable;
import bear.core.VariablesLayer;
import bear.session.DynamicVariable;
import bear.session.Variables;
import chaschev.lang.OpenBean;
import chaschev.lang.reflect.ClassDesc;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;

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
            Field[] contextFields = OpenBean.getClassDesc(this.getClass()).fields;

            for (Field field : OpenBean.getClassDesc(object.getClass()).fields) {
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
}
