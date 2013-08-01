package cap4j;

import cap4j.session.Result;
import cap4j.session.SessionContext;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import cap4j.strategy.BaseStrategy;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class Stage {
    String name;
    String description;

    SystemEnvironments environments = new SystemEnvironments(null);

    ExecutorService executor;

    public Stage(String name) {
        this.name = name;
    }

    public void runTask(final Task<TaskResult> task){
        final GlobalContext globalContext = GlobalContextFactory.INSTANCE.configure(environments);

        executor = Executors.newCachedThreadPool();

        BaseStrategy.setBarriers(this, GlobalContext.INSTANCE.localCtx);

        for (final SystemEnvironment environment : environments.getImplementations()) {
            final SessionContext sessionContext = new SessionContext(
                newSessionVars(globalContext, environment)
            );

            executor.execute(new Runnable() {
                @Override
                public void run() {

                    final VarContext ctx = new VarContext(
                        sessionContext.variables, environment
                    );

                    environment.ctx = ctx;

                    Thread.currentThread().setName(ctx.threadName());

                    final Result run = new TaskRunner(
                        ctx
                    ).run(task);
                }
            });
        }

        executor.shutdown();
    }

    public static Variables newSessionVars(GlobalContext globalContext, SystemEnvironment environment) {
        return new Variables(environment.getName() + " vars", globalContext.variables);
    }

    public Stage add(SystemEnvironment environment) {
        environments.add(environment);

        return this;
    }

    public SystemEnvironments getEnvironments() {
        return environments;
    }
}
