package bear.plugins.sh;

import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WriteStringResult extends CommandLineResult<WriteStringResult> {
    boolean wrote;

    public WriteStringResult(CommandLineResult<?> r, boolean wrote) {
        super(r);
        this.wrote = wrote;
    }

}
