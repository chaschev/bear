package bear.task;

import bear.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class NamedSupplier<I, O extends TaskResult<?>> implements SingleTaskSupplier<I, O>{
    String name;
    SingleTaskSupplier<I, O> supplier;

    public NamedSupplier(String name, SingleTaskSupplier<I, O> supplier) {
        this.name = name;
        this.supplier = supplier;
    }

    public Task<I,O> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<I, O> def) {
        return supplier.createNewSession($, parent, def);
    }

    public String getName() {
        return name;
    }
}
