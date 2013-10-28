package bear.core;

import bear.plugins.AbstractContext;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public interface Fun<T, CONTEXT extends AbstractContext> {
    Object UNDEFINED = new UndefinedReturnValue();

    T apply(CONTEXT $);

    public static class UndefinedReturnValue {
        @Override
        public String toString() {
            return "UNDEFINED";
        }
    }
}
