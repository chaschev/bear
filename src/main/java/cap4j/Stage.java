package cap4j;

import cap4j.session.Result;
import cap4j.session.SessionContext;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import cap4j.strategy.BaseStrategy;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class Stage {
    String name;
    String description;

    SystemEnvironments environments;
    GlobalContextFactory globalContextFactory;

    Executor executor = Executors.newCachedThreadPool();

    public Stage(String name) {
        this.name = name;
    }

    public void runTask(final Task<TaskResult> task){
        final GlobalContext globalContext = globalContextFactory.create(environments);

        BaseStrategy.setBarriers(this, new Variables.Context(globalContext, null, null));

        for (final SystemEnvironment environment : environments.getImplementations()) {
            final SessionContext sessionContext = new SessionContext(globalContext);

            executor.execute(new Runnable() {
                @Override
                public void run() {

                    final Variables.Context ctx = new Variables.Context(
                        globalContext, sessionContext, environment
                    );

                    Thread.currentThread().setName(ctx.threadName());

                    final Result run = new TaskRunner(
                        ctx
                    ).run(task);
                }
            });
        }
    }

    public Stage add(SystemEnvironment environment) {
        environments.add(environment);

        return this;
    }

    public SystemEnvironments getEnvironments() {
        return environments;
    }
}
