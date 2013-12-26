package bear.task;

import bear.core.SessionContext;
import com.google.common.base.Throwables;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class NamedCallable<I, O extends TaskResult> implements TaskCallable<I, O>{
    String name;
    TaskCallable<I, O> callable;

    public NamedCallable(String name, TaskCallable<I, O> callable) {
        this.name = name;
        this.callable = callable;
    }

    public NamedCallable(TaskCallable<I, O> callable) {
        this.name = Throwables.getStackTraceAsString(new Exception());
        this.callable = callable;
    }

    @Override
    public O call(SessionContext $, Task<I, O> task) throws Exception {
        return callable.call($, task);
    }

    public String getName() {
        return name;
    }

    public static <I, O extends TaskResult> NamedCallable<I, O> named(String name, TaskCallable<I, O> callable){
        return new NamedCallable<I, O>(name, callable);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Callable{");
        sb.append("name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
