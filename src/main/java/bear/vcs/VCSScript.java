package bear.vcs;

import bear.plugins.sh.ResultParser;
import bear.plugins.sh.Script;
import bear.plugins.sh.SystemSession;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class VCSScript<T extends CommandLineResult<?>> extends Script<T, VCSScript<T>> {
    protected final VCSSession vcsSession;


    public VCSScript(SystemSession sys, VCSSession vcsSession) {
        super(sys);
        timeoutForBuild();
        this.vcsSession = vcsSession;
    }

    @Override
    public T run() {
        callback(vcsSession.passwordCallback());
        return sys.run(this);
    }

    public T run(ResultParser<T> parser) {
        setParser(parser);
        callback(vcsSession.passwordCallback());
        return sys.run(this);
    }
}
