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
    CompositeConsoleArrival consoleArrival;
    CompositeConsoleArrival.EqualityGroups equalityGroups;

    public final DynamicVariable<AtomicInteger> partiesLeft = Variables.dynamic(AtomicInteger.class).defaultTo(new AtomicInteger());
    public final DynamicVariable<AtomicInteger> partiesCount = null;

    public CompositeConsoleCallContext(List<? extends AbstractConsole> consoles) {
        this.consoles = consoles;
//        consoleArrival = new CompositeConsoleArrival();
//        partiesCount = Variables.dynamic(AtomicInteger.class).defaultTo(consoles.size());
    }
}
