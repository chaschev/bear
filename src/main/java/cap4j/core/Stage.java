package cap4j.core;

import cap4j.session.*;
import cap4j.strategy.BaseStrategy;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class Stage {
    public String name;
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
            global.taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName(environment.ctx().threadName());

                    final Result run = new TaskRunner(environment.ctx()).run(task);
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Stage{");
        sb.append("name='").append(name).append('\'');
        if(description != null)
            sb.append(", description='").append(description).append('\'');
        sb.append(", environments=").append(environments);
        sb.append('}');
        return sb.toString();
    }

    public SystemEnvironment findRemoteEnvironment() {
        return Iterables.find(environments.getImplementations(), Predicates.instanceOf(GenericUnixRemoteEnvironment.class));

    }
}
