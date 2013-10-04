package bear.console;

import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class AbstractConsoleCommand<T extends CommandLineResult>{
    protected long timeoutMs;

    public abstract String asText();

    public long getTimeoutMs() {
        return timeoutMs;
    }
}
