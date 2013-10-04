package bear.console;

import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class ConsoleCallback {
    public abstract void progress(AbstractConsole.Terminal console, String buffer, String wholeText);
    public void whenDone(CommandLineResult result){}
}
