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
import bear.plugins.sh.SystemSession;
import bear.session.Address;
import bear.session.SshAddress;
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

import static bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin.newUnixRemote;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Stage {
    private static final Logger logger = LoggerFactory.getLogger(Stage.class);
    public static final Function<SessionContext,String> SESSION_ID = new Function<SessionContext, String>() {
    public String apply(SessionContext $) {
        return $.id;
    }
};

    public String name;
    String description;

    List<Address> addresses = new ArrayList<Address>();

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
        List<Address> addresses = this.addresses;

        final List<ListenableFuture<SessionContext>> futures = new ArrayList<ListenableFuture<SessionContext>>(addresses.size());
        final List<SystemSession> consoles = new ArrayList<SystemSession>(addresses.size());

        List<SessionContext> $s = new ArrayList<SessionContext>();

//        public SessionContext newCtx(TaskRunner runner){
//            $ = new SessionContext(global, this, runner);
//            runner.set$($);
//            return $;
//        }

        for (Address address : addresses) {
            final TaskRunner runner = new TaskRunner(null, global);

            SessionContext $ = new SessionContext(global, address, runner);

            $s.add($);
        }

        final CompositeConsoleArrival<SessionContext> consoleArrival = new CompositeConsoleArrival<SessionContext>($s, futures, consoles,
            new Function<SessionContext, String>() {
                @Override
                public String apply(SessionContext $) {
                    return $.executionContext.text.apply($).toString();
                }
            }, SESSION_ID
        );

        CompositeTaskRunContext taskRunContext = new CompositeTaskRunContext(global, task, consoleArrival);

        for (SessionContext $ : $s) {
            $.setTaskRunContext(taskRunContext);
        }

        return taskRunContext;
    }

    public Stage add(String address) {
        return add(address, address);
    }

    public Stage add(String name, String address) {
        return add(newUnixRemote(name, address));
    }

    public Stage add(Address environment) {
        addresses.add(environment);

        return this;
    }

    public List<Address> getEnvironments() {
        return addresses;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Stage{");
        sb.append("name='").append(name).append('\'');
        if (description != null)
            sb.append(", description='").append(description).append('\'');
        sb.append(", environments=").append(addresses);
        sb.append('}');
        return sb.toString();
    }

    public Address findRemoteEnvironment() {
        return Iterables.find(addresses, Predicates.instanceOf(SshAddress.class));

    }

    public Stage addHosts(String... hosts) {
        for (String host : hosts) {
            add(host);
        }

        return this;
    }
}
