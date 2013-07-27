package cap4j;

import cap4j.session.Result;
import cap4j.session.SessionContext;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import cap4j.task.Task;
import cap4j.task.TaskRunner;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class Stage {
    String name;
    String description;

    SystemEnvironments environments;
    GlobalContextFactory globalContextFactory;

    public Stage(String name) {
        this.name = name;
    }

    public void runTask(Task<Task.TaskResult> task){
        final GlobalContext globalContext = globalContextFactory.create(environments);

        for (SystemEnvironment environment : environments.getImplementations()) {
            final SessionContext sessionContext = new SessionContext(globalContext);

            final Result run = new TaskRunner(
                new Variables.Context(
                    globalContext, sessionContext, environment
                )
            ).run(task);
        }
    }

    public Stage add(SystemEnvironment environment) {
        environments.add(environment);

        return this;
    }
}
