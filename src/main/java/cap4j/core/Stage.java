/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cap4j.core;

import cap4j.plugins.Plugin;
import cap4j.session.GenericUnixRemoteEnvironment;
import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import cap4j.strategy.BaseStrategy;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import com.chaschev.chutils.util.OpenBean2;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Stage {
    private static final Logger logger = LoggerFactory.getLogger(Stage.class);

    public String name;
    String description;

    SystemEnvironments environments = new SystemEnvironments(null);

    GlobalContext global;

    public Stage(String name, GlobalContext global) {
        this.name = name;
        this.global = global;
    }

    /**
     * Runs a task from task variable
     */
    public void run() {
        final String var = global.localCtx.var(global.cap.task);
        Task task = (Task) OpenBean2.getFieldValue2(global.tasks, var);
        runTask(task);
    }

    public void runTask(final Task<TaskResult> task) {
        GlobalContextFactory.INSTANCE.configure(environments);

        BaseStrategy.setBarriers(this, global);

        for (final SystemEnvironment environment : environments.getImplementations()) {
            global.taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName(environment.ctx().threadName());

                    environment.ctx().system.connect();

                    if (environment.ctx().var(environment.cap.verifyPlugins)) {
                        int count = 0;
                        for (Plugin plugin : global.getPlugins()) {
                            try {
                                plugin.getSetup()
                                    .setCtx(environment.$)
                                    .verifyExecution(true);
                            } catch (Exception e) {
                                count++;
                                logger.error("error verifying plugin {} installation: {}", plugin, e.toString());
                            }
                        }

                        if (count > 0) {
                            throw new RuntimeException("there were " + count + " errors during plugin verification");
                        }
                    }

                    final Result run = new TaskRunner(environment.ctx(), global).run(task);
                }
            });
        }
    }

    public Stage add(SystemEnvironment environment) {
        environments.add(environment);
        environment.cap = global.cap;

        return this;
    }

    public SystemEnvironments getEnvironments() {
        return environments;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Stage{");
        sb.append("name='").append(name).append('\'');
        if (description != null)
            sb.append(", description='").append(description).append('\'');
        sb.append(", environments=").append(environments);
        sb.append('}');
        return sb.toString();
    }

    public SystemEnvironment findRemoteEnvironment() {
        return Iterables.find(environments.getImplementations(), Predicates.instanceOf(GenericUnixRemoteEnvironment.class));

    }
}
