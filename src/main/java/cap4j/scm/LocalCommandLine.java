package cap4j.scm;

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
