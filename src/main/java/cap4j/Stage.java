package cap4j;

import cap4j.session.Result;
import cap4j.session.SessionContext;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import cap4j.strategy.BaseStrategy;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class Stage {
    String name;
    String description;

    SystemEnvironments environments = new SystemEnvironments(null);

    public Stage(String name) {
        this.name = name;
    }

    public void runTask(final Task<TaskResult> task){
        GlobalContextFactory.INSTANCE.configure(environments);

        final GlobalContext global = GlobalContext.INSTANCE;

        BaseStrategy.setBarriers(this, global.localCtx);

        for (final SystemEnvironment environment : environments.getImplementations()) {
            final SessionContext sessionContext = new SessionContext(
                newSessionVars(global, environment)
            );

            global.taskExecutor.execute(new Runnable() {
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
