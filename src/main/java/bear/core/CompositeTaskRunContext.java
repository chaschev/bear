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

import bear.console.CompositeConsoleArrival;
import bear.main.BearCommandLineConfigurator;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.SystemEnvironment;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import chaschev.util.Exceptions;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class CompositeTaskRunContext {
    private final GlobalContext global;
    private final TaskDef task;
    private final CompositeConsoleArrival<SessionContext> consoleArrival;

    public final DynamicVariable<Stats> stats;

    public CompositeTaskRunContext(GlobalContext global, TaskDef task, CompositeConsoleArrival<SessionContext> consoleArrival) {
        this.global = global;
        this.task = task;
        this.consoleArrival = consoleArrival;

        stats = Variables.dynamic(Stats.class).defaultTo(new Stats(consoleArrival.getArrivedEntries().size()));
    }

    public CompositeConsoleArrival<SessionContext> getConsoleArrival() {
        return consoleArrival;
    }

    public void addArrival(int i, SessionContext $) {
        consoleArrival.addArrival(i, $);

        boolean isOk = $.getExecutionContext().rootExecutionContext.getDefaultValue().taskResult.ok();

        stats.getDefaultValue().addArrival(isOk);
        stats.fireExternalModification();
    }

    public void submitTasks() {
        List<ListenableFuture<SessionContext>> futures = consoleArrival.getFutures();
        List<SessionContext> $s = consoleArrival.getEntries();


        for (int i = 0; i < $s.size(); i++) {
            final SessionContext $ = $s.get(i);
            final SystemEnvironment sys = $.getSys();
            final TaskRunner runner = $.getRunner();

            final int finalI = i;

            final ListenableFuture<SessionContext> future = global.taskExecutor.submit(new Callable<SessionContext>() {
                @Override
                public SessionContext call() {
                    try {
                        Thread.currentThread().setName($.threadName());

                        $.sys.connect();

                        if ($.var(sys.bear.verifyPlugins)) {
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
                        BearCommandLineConfigurator.logger.warn("", e);
                        if (e instanceof Error) {
                            throw (Error) e;
                        }
                        throw Exceptions.runtime(e);
                    }finally {
                        addArrival(finalI, $);
                    }

                    return $;
                }
            });

            futures.add(future);
        }
    }

    public static class Stats{
        public final AtomicInteger partiesArrived = new AtomicInteger();
        public final AtomicInteger partiesOk = new AtomicInteger();
        public int partiesPending;
        public int partiesFailed = 0;
        public final AtomicInteger partiesCount;

        public Stats(int count) {
            partiesPending = count;
            partiesCount = new AtomicInteger(count);
        }

        public void addArrival(boolean isOk) {
            partiesArrived.incrementAndGet();
            partiesPending = partiesCount.get() - partiesArrived.get();

            if(isOk){
                partiesOk.incrementAndGet();
            }

            partiesFailed = partiesArrived.get() - partiesOk.get();
        }
    }

    public GlobalContext getGlobal() {
        return global;
    }

    public TaskDef getTask() {
        return task;
    }
}
