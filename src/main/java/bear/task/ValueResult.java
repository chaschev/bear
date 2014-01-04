package bear.task;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ValueResult<V> extends TaskResult<ValueResult> {
    V value;

    public ValueResult(V value) {
        super(OK);
        this.value = value;
    }

    public ValueResult(Throwable e) {
        super(e);
    }

    public V getValue() {
        return value;
    }

    public static <V> ValueResult<V> result(V v){
        return new ValueResult<V>(v);
    }
}
