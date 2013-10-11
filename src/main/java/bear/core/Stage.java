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
import bear.session.GenericUnixRemoteEnvironment;
import bear.session.SystemEnvironment;
import bear.task.TaskDef;
import bear.task.TaskRunner;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
    public CompositeTaskRunContext prepareToRun() {
        return prepareToRunTask(global.localCtx.var(global.bear.task));
    }

    public CompositeTaskRunContext prepareToRunTask(final TaskDef task) {
        List<? extends AbstractConsole> consoles = systemEnvironments;

        final List<ListenableFuture<SessionContext>> futures = new ArrayList<ListenableFuture<SessionContext>>(consoles.size());

        List<SessionContext> $s = new ArrayList<SessionContext>();

        for (AbstractConsole console : consoles) {
            final SystemEnvironment environment = (SystemEnvironment) console;

            final TaskRunner runner = new TaskRunner(null, global);

            $s.add(environment.newCtx(runner));
        }

        final CompositeConsoleArrival<SessionContext> consoleArrival = new CompositeConsoleArrival<SessionContext>($s, futures, consoles, new Function<SessionContext, String>() {
            @Override
            public String apply(SessionContext $) {
                return $.executionContext.text.apply($).toString();
            }
        });

        return new CompositeTaskRunContext(global, task, consoleArrival);
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
