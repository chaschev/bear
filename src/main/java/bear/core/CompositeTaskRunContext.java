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
import bear.session.DynamicVariable;
import bear.session.Variables;

import java.util.concurrent.atomic.AtomicInteger;

public class CompositeTaskRunContext {
    private final CompositeConsoleArrival<SessionContext> consoleArrival;

    public final DynamicVariable<Stats> stats;

    public CompositeTaskRunContext(CompositeConsoleArrival<SessionContext> consoleArrival) {
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
}
