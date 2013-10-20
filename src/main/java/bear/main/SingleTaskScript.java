package bear.main;

import bear.task.TaskDef;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SingleTaskScript extends Script {
    TaskDef taskDef;

    public SingleTaskScript(TaskDef taskDef) {
        this.taskDef = taskDef;
    }

    @Override
    protected void configure() throws Exception {
        bear.stage.defaultTo("two");//todo fixme!!
        bear.task.defaultTo(taskDef);
        bear.checkDependencies.defaultTo(false);
    }
}
