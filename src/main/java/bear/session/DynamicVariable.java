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

import bear.context.*;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DynamicVariable<T> implements Nameable<T> {

    public static final String NOT_SET = "__NOT_SET";
    public static final String TEMP_VAR = "TEMP_VAR";

    // todo re(-)move this nightmare
    // only listening for context changes makes sense
    public static abstract class ChangeListener<T>{
        public abstract void changedValue(DynamicVariable<T> var, T oldValue, T newValue);
        public void changedDynamic(DynamicVariable<T> var, Fun<? extends AbstractContext, T> oldFun, Fun<? extends AbstractContext, T> newFun){}
    }

    protected List<ChangeListener<T>> listeners;

    private static final Logger logger = LoggerFactory.getLogger(DynamicVariable.class);

    public boolean frozen;

    @Nonnull
    protected String name;
    protected String desc;

    protected Fun<? extends AbstractContext, T> fun;

    //todo: change to object, make UNDEFINED default, throw an error when evaluating
    T defaultValue;

    Class<? extends AbstractContext> memoizeIn;

    public DynamicVariable(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public DynamicVariable() {
        this.name = NOT_SET;
        this.desc = "";
    }

    public DynamicVariable(String desc) {
        this.name = NOT_SET;
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
    public final String name() {
        return name;
    }

    public final T apply(AbstractContext $) {
        return (T) apply($, defaultValue);
    }

    public final Object apply(AbstractContext $, Object _default) {
        if(_default != Fun.UNDEFINED){
            if (defaultValue == null && fun == null) {
                throw new UnsupportedOperationException("you should implement dynamic variable :" + name + " or set its default value");
            }
        }

        if (fun != null) {
            final T r = ((Fun<AbstractContext, T>)fun).apply($);

            if(logger.isTraceEnabled()){
                logger.trace(":{} (dynamic): {}", $.getName(), name, r);
            }

            return r;
        }

        if($.isGlobal()){
            if(logger.isTraceEnabled()){
                logger.trace("{}: :{} (default): {}", $.getName(), name, defaultValue);
            }

            return defaultValue;
        }else{
            if(logger.isTraceEnabled()){
                logger.trace("{}: :{} = UNDEFINED", $.getName(), name);
            }

            return _default;
        }
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

    public DynamicVariable<T> defaultTo(T newValue, boolean force) {
        Preconditions.checkArgument(memoizeIn == null, "memoized vars are dynamic");

        if (fun != null) {
            if (force) {
                fun = null;
            } else {
                throw new IllegalStateException("use force to override dynamic implementation");
            }
        }

        T oldValue = this.defaultValue;
        this.defaultValue = newValue;

        onValueChange(oldValue, newValue);

        return this;
    }

    public DynamicVariable<T> setDynamic(Fun<? extends AbstractContext, T> impl) {
        Fun<? extends AbstractContext, T> oldFun = this.fun;
        this.fun = impl;

        if (impl instanceof VarFun<?, ?>) {
            VarFun<T, ?> varFun = (VarFun<T, ?>) impl;

            varFun.setVar(this);
        }

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

    public DynamicVariable<T> desc(String desc) {
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
        if(memoizeIn !=null) sb.append(", memoizeIn=").append(memoizeIn.getSimpleName());
        sb.append('}');
        return sb.toString();
    }

    public boolean isSet() {
        return fun != null || defaultValue != null;
    }

    public DynamicVariable<T> setEqualTo(final DynamicVariable<T> variable) {
        setDynamic(new Fun<AbstractContext, T>() {
            public T apply(AbstractContext $) {
                return $.var(variable);
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

    public boolean isDefined(){
        return !isUndefined();
    }

    public final boolean isUndefined(){
        return defaultValue == Fun.UNDEFINED;
    }

    public Class<? extends AbstractContext> memoizeIn() {
        return memoizeIn;
    }

    public DynamicVariable<T> memoizeIn(Class<? extends AbstractContext> memoizeIn) {
        this.memoizeIn = memoizeIn;
        return this;
    }

    public boolean isNameSet(){
        return name != null && !NOT_SET.equals(name);
    }

    public boolean isTemporal(){
        return name == TEMP_VAR;
    }

    public DynamicVariable<T> temp() {
        name = TEMP_VAR;
        return this;
    }


}
