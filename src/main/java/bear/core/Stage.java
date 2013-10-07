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

package bear.core;

import bear.console.AbstractConsole;
import bear.console.CompositeConsoleArrival;
import bear.plugins.Plugin;
import bear.session.GenericUnixRemoteEnvironment;
import bear.session.Result;
import bear.session.SystemEnvironment;
import bear.task.*;
import bear.vcs.CommandLineResult;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Stage {
    private static final Logger logger = LoggerFactory.getLogger(Stage.class);

    public String name;
    String description;

    List<SystemEnvironment> systemEnvironments = new ArrayList<SystemEnvironment>();

    GlobalContext global;

    public Stage(String name, GlobalContext global) {
        this.name = name;
        this.global = global;
    }

    /**
     * Runs a task from task variable
     */
    public CompositeTaskRunContext run() {
        CompositeTaskRunContext taskRunContext = runTask(global.localCtx.var(global.bear.task));

        CompositeConsoleArrival<SessionContext> consoleArrival = taskRunContext.getConsoleArrival();

        consoleArrival.await(global.localCtx.var(global.bear.taskTimeoutSec));

        return taskRunContext;
    }

    public CompositeTaskRunContext runTask(final TaskDef task) {
        List<? extends AbstractConsole> consoles = systemEnvironments;

        final List<ListenableFuture<SessionContext>> futures = new ArrayList<ListenableFuture<SessionContext>>(consoles.size());

        final CompositeConsoleArrival<SessionContext> consoleArrival = new CompositeConsoleArrival<SessionContext>(futures, consoles, new Function<SessionContext, String>() {
            @Override
            public String apply(SessionContext $) {
                return $.executionContext.text.apply($).toString();
            }
        });

        final CompositeTaskRunContext compositeTaskRunContext = new CompositeTaskRunContext(consoleArrival);

        for (int i = 0; i < consoles.size(); i++) {
            AbstractConsole console = consoles.get(i);
            final SystemEnvironment environment = (SystemEnvironment) console;

            final int finalI = i;
            ListenableFuture<SessionContext> future = global.taskExecutor.submit(new Callable<SessionContext>() {
                @Override
                public SessionContext call() {
                    final SessionContext $;
                    final TaskRunner runner = new TaskRunner(null, global);

                    $ = environment.newCtx(runner);

                    try {


                        Thread.currentThread().setName($.threadName());

                        $.sys.connect();

                        if ($.var(environment.bear.verifyPlugins)) {
                            DependencyResult r = new DependencyResult(Result.OK);

                            for (Plugin plugin : global.getGlobalPlugins()) {
                                r.join(plugin.checkPluginDependencies());

                                if (!task.isSetupTask()) {
                                    r.join(plugin.getInstall().newSession($, $.currentTask)
                                        .asInstalledDependency().checkDeps());
                                }
                            }

                            if (r.nok()) {
                                throw new DependencyException(r.toString());
                            }
                        }

                        final TaskResult run = runner.run(task);

                        if (!run.ok()) {
                            System.out.println(run);
                        }
                    } catch (Throwable e) {
                        $.executionContext.rootExecutionContext.getDefaultValue().taskResult = new CommandLineResult(Result.ERROR, e.toString());
                        logger.warn("", e);
                        if (e instanceof Error) {
                            throw (Error) e;
                        }
                        throw Exceptions.runtime(e);
                    }finally {
                        compositeTaskRunContext.addArrival(finalI, $);
                    }


                    return $;
                }
            });

            futures.add(future);
        }


        return compositeTaskRunContext;
    }

    public Stage add(SystemEnvironment environment) {
        systemEnvironments.add(environment);
        environment.bear = global.bear;

        return this;
    }

    public List<SystemEnvironment> getEnvironments() {
        return systemEnvironments;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Stage{");
        sb.append("name='").append(name).append('\'');
        if (description != null)
            sb.append(", description='").append(description).append('\'');
        sb.append(", environments=").append(systemEnvironments);
        sb.append('}');
        return sb.toString();
    }

    public SystemEnvironment findRemoteEnvironment() {
        return Iterables.find(systemEnvironments, Predicates.instanceOf(GenericUnixRemoteEnvironment.class));

    }
}
