package cap4j.session;

import cap4j.VarContext;
import cap4j.Nameable;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* User: chaschev
* Date: 7/21/13
*/
public class DynamicVariable<T> implements Nameable {
    private static final Logger logger = LoggerFactory.getLogger(DynamicVariable.class);

    public boolean frozen;

    public final String name;
    public final String title;

    protected Function<VarContext, T> dynamicImplementation;

    T defaultValue;

    private boolean memoize;

    public DynamicVariable(String name, String title) {
        this.name = name;
        this.title = title;
    }

    public DynamicVariable(String title) {
        this.name = "-";
        this.title = title;
    }

    public DynamicVariable(Nameable varName, String title) {
        this.name = varName.name();
        this.title = title;
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

            logger.debug("returning dynamic value for :{}: {}", r);
            return r;
        }

        logger.debug("returning default value for :{}: {}", defaultValue);

        return defaultValue;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public DynamicVariable<T> defaultTo(T defaultValue) {
        this.defaultValue = defaultValue;
        dynamicImplementation = null;
        memoize = false;
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
}
