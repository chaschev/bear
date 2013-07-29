package cap4j.session;

import cap4j.Nameable;
import cap4j.Variables;

import javax.annotation.Nullable;

/**
 * User: chaschev
 * Date: 7/27/13
 */
public class StaticVariable<T> extends DynamicVariable<T> {
    T value;

    public StaticVariable(String title) {
        super(title);
    }

    public StaticVariable(Nameable varName, String title) {
        super(varName, title);
    }

    @Nullable
    @Override
    public T apply(Variables.Context context) {
        return value;
    }
}
