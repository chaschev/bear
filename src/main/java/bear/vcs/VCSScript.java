package bear.vcs;

import bear.cli.Script;
import bear.plugins.sh.SystemSession;
import com.google.common.base.Function;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class VCSScript<T extends CommandLineResult> extends Script<T, VCSScript<T>> {
    protected final VCSSession vcsSession;


    public VCSScript(SystemSession sys, VCSSession vcsSession) {
        super(sys);
        this.vcsSession = vcsSession;
    }

    @Override
    public T run() {
        return sys.run(this, vcsSession.passwordCallback());
    }

    public T run(Function<String, T> parser) {
        setParser(parser);
        return sys.run(this, vcsSession.passwordCallback());
    }
}
