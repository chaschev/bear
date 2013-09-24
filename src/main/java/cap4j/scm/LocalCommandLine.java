package cap4j.scm;

import cap4j.cli.CommandLine;

/**
 * User: achaschev
 * Date: 8/4/13
 */
public class LocalCommandLine<T extends CommandLineResult> extends CommandLine<T> {
    public LocalCommandLine() {

    }

    @Override
    public CommandLine<T> stty() {
        return this;
    }
}
