package cap4j.console;

import cap4j.vcs.CommandLineResult;

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
