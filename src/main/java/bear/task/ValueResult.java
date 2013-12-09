package bear.task;

import bear.session.Result;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ValueResult<V> extends TaskResult {
    V value;

    public ValueResult(Result result, V value) {
        super(result);
        this.value = value;
    }

    public ValueResult(Throwable e) {
        super(e);
    }

    public V getValue() {
        return value;
    }
}
