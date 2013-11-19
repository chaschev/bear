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

package bear.console;

import bear.session.DynamicVariable;
import bear.session.Variables;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompositeConsoleCallContext {
    final ProgressMonitor progressMonitor = new ProgressMonitor();
    final List<? extends AbstractConsole> consoles;
    ConsolesDivider consoleArrival;
    ConsolesDivider.EqualityGroups equalityGroups;

    public final DynamicVariable<AtomicInteger> partiesLeft = Variables.dynamic(AtomicInteger.class).defaultTo(new AtomicInteger());
    public final DynamicVariable<AtomicInteger> partiesCount = null;

    public CompositeConsoleCallContext(List<? extends AbstractConsole> consoles) {
        this.consoles = consoles;
//        consoleArrival = new CompositeConsoleArrival();
//        partiesCount = Variables.dynamic(AtomicInteger.class).defaultTo(consoles.size());
    }
}
