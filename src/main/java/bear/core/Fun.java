package bear.core;

import com.google.common.base.Function;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public interface Fun<T, CONTEXT extends AbstractContext> extends Function<CONTEXT, T> {
    Object UNDEFINED = new UndefinedReturnValue();

    T apply(CONTEXT $);

    public static class UndefinedReturnValue {
        @Override
        public String toString() {
            return "UNDEFINED";
        }
    }
}
