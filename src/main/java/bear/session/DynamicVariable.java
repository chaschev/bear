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

package bear.session;

import bear.core.Nameable;
import bear.core.VarFun;
import bear.plugins.AbstractContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DynamicVariable<T> implements Nameable<T> {

    public static abstract class ChangeListener<T>{
        public abstract void changedValue(DynamicVariable<T> var, T oldValue, T newValue);
        public void changedDynamic(DynamicVariable<T> var, VarFun<T, ? extends AbstractContext> oldFun, VarFun<T, ? extends AbstractContext> newFun){}
    }

    protected List<ChangeListener<T>> listeners;

    private static final Logger logger = LoggerFactory.getLogger(DynamicVariable.class);

    public boolean frozen;

    @Nonnull
    public String name;
    public String desc;

    protected VarFun<T, ? extends AbstractContext> dynamicImplementation;

    T defaultValue;

    private boolean memoize;

    public DynamicVariable(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public DynamicVariable() {
        this.name = "-";
        this.desc = "";
    }

    public DynamicVariable(String desc) {
        this.name = "-";
        this.desc = desc;
    }

    public DynamicVariable(Nameable varName, String desc) {
        this.name = varName.name();
        this.desc = desc;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void validate(T value) {
        //todo embed
    }

    @Override
    public String name() {
        return name;
    }

    public final T apply(Object $) {
        if (defaultValue == null && dynamicImplementation == null) {
            throw new UnsupportedOperationException("you should implement dynamic variable :" + name + " or set its default value");
        }

        if (dynamicImplementation != null) {
            if (memoize && defaultValue != null) {
                return defaultValue;
            }

            if (dynamicImplementation instanceof VarFun<?, ?>) {
                ((VarFun<?, AbstractContext>) dynamicImplementation).set$($);
            }

            final T r = dynamicImplementation.apply();

            if (memoize) {
                defaultValue = r;
            }

            logger.trace(":{} (dynamic): {}", name, r);
            return r;
        }

        logger.trace(":{} (default): {}", name, defaultValue);

        return defaultValue;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public DynamicVariable<T> defaultTo(T defaultValue) {
        return defaultTo(defaultValue, true);
    }

    public DynamicVariable<T> set(T defaultValue) {
        return defaultTo(defaultValue);
    }

    public DynamicVariable<T> defaultTo(T defaultValue, boolean force) {
        if (dynamicImplementation != null) {
            if (force) {
                dynamicImplementation = null;
                memoize = false;
            } else {
                throw new IllegalStateException("use force to override dynamic implementation");
            }
        }

        T oldValue = this.defaultValue;
        this.defaultValue = defaultValue;

        onValueChange(oldValue, defaultValue);

        return this;
    }

    public DynamicVariable<T> setDynamic(VarFun<T, ? extends AbstractContext> impl) {
        VarFun<T, ? extends AbstractContext> oldFun = this.dynamicImplementation;
        this.dynamicImplementation = impl;

        impl.setVar(this);

        defaultValue = null;

        if(listeners != null){
            for (ChangeListener<T> listener : listeners) {
                listener.changedDynamic(this, oldFun, impl);
            }
        }

        return this;
    }

    public DynamicVariable<T> fireExternalModification(T oldValue, T newValue){
        onValueChange(oldValue, newValue);

        return this;
    }

    public void fireExternalModification() {
        fireExternalModification(null, defaultValue);
    }

    private void onValueChange(T oldValue, T newValue) {
        if(listeners != null){
            for (ChangeListener<T> listener : listeners) {
                listener.changedValue(this, oldValue, newValue);
            }
        }
    }

    public DynamicVariable<T> memoize(boolean memoize) {
//        Preconditions.checkArgument(dynamicImplementation != null, "memoization works with dynamic implementations");

        this.memoize = memoize;
        return this;
    }

    public DynamicVariable<T> setDesc(String desc) {
        this.desc = desc;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DynamicVariable that = (DynamicVariable) o;

        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DynamicVariable{");
        sb.append("name='").append(name).append('\'');
        sb.append(", defaultValue=").append(defaultValue);
        sb.append(", memoize=").append(memoize);
        sb.append('}');
        return sb.toString();
    }

    public boolean isSet() {
        return dynamicImplementation != null || defaultValue != null;
    }

    public DynamicVariable<T> setEqualTo(final DynamicVariable<T> variable) {
        setDynamic(new VarFun<T, AbstractContext>() {
            public T apply() {
                return variable.apply($);
            }
        });
        return this;
    }

    public DynamicVariable<T> setName(String name) {
        this.name = name;
        return this;
    }

    public DynamicVariable<T> addListener(ChangeListener<T> listener){
        if(this.listeners == null){
            this.listeners = new ArrayList<ChangeListener<T>>(2);
        }

        listeners.add(listener);

        return this;
    }
}
