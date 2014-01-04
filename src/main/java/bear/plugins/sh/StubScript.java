package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import bear.vcs.VCSScript;
import bear.vcs.VCSSession;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class StubScript<T extends CommandLineResult<?>> extends VCSScript<T> {
    private final T result;

    public StubScript(SystemSession sys, VCSSession vcsSession, T result) {
        super(sys, vcsSession);
        this.result = result;
    }


    @Override
    public T parseResult(String text, SessionContext $, String script) {
        return result;
    }
}
