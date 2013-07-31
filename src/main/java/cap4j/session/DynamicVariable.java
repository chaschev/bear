package cap4j.session;

import cap4j.VarContext;
import cap4j.Nameable;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
* User: chaschev
* Date: 7/21/13
*/
public class DynamicVariable<T> implements Nameable<T> {
    private static final Logger logger = LoggerFactory.getLogger(DynamicVariable.class);

    public boolean frozen;

    @Nonnull
    public final String name;
    public String desc;

    protected Function<VarContext, T> dynamicImplementation;

    T defaultValue;

    private boolean memoize;

    public DynamicVariable(String name, String desc) {
        this.name = name;
        this.desc = desc;
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

    public void validate(T value){
        //todo embed
    }

    @Override
    public String name() {
        return name;
    }

    public final T apply(VarContext context) {
        if(defaultValue == null && dynamicImplementation == null){
            throw new UnsupportedOperationException("you should implement dynamic variable :" + name + " or set its default value");
        }

        if(dynamicImplementation != null){
            if(memoize && defaultValue != null){
                return defaultValue;
            }

            final T r = dynamicImplementation.apply(context);

            if(memoize){
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
        return defaultTo(defaultValue, false);
    }

    public DynamicVariable<T> defaultTo(T defaultValue, boolean force) {
        if(dynamicImplementation !=null) {
            if (force) {
                dynamicImplementation = null;
                memoize = false;
            }else{
                throw new IllegalStateException("use force to override dynamic implementation");
            }
        }

        this.defaultValue = defaultValue;

        return this;
    }

    public DynamicVariable<T> setDynamic(Function<VarContext, T> dynamicImplementation) {
        this.dynamicImplementation = dynamicImplementation;
        defaultValue = null;
        return this;
    }

    public DynamicVariable<T> memoize(boolean memoize) {
        Preconditions.checkArgument(dynamicImplementation != null, "memoization works with dynamic implementations");

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
}
