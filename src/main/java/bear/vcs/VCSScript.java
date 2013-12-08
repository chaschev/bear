package bear.vcs;

import bear.plugins.sh.Script;
import bear.plugins.sh.SystemSession;
import com.google.common.base.Function;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class VCSScript<T extends CommandLineResult> extends Script<T, VCSScript<T>> {
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

    public T run(Function<String, T> parser) {
        setParser(parser);
        callback(vcsSession.passwordCallback());
        return sys.run(this);
    }
}
