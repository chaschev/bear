package bear.console;

import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompositeConsoleCallContext {
    final CompositeConsole.Progress progress;
    final List<AbstractConsole> consoles;
    final CompositeConsoleArrival consoleArrival;
    CompositeConsoleArrival.EqualityGroups equalityGroups;

    public CompositeConsoleCallContext(CompositeConsole.Progress progress, List<AbstractConsole> consoles) {
        this.progress = progress;
        this.consoles = consoles;
//            consoleArrival = new CompositeConsoleArrival();
        consoleArrival = null;
    }
}
