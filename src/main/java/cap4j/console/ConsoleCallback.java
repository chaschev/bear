package cap4j.console;

import cap4j.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class ConsoleCallback {
    public abstract void progress(AbstractConsole.Terminal console, String buffer, String wholeText);
    public void whenDone(CommandLineResult result){}
}
