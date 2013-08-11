package cap4j.strategy;

import cap4j.core.VarContext;
import cap4j.session.Result;

import static cap4j.core.CapConstants.releasePath;
import static cap4j.core.CapConstants.revision;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public abstract class RemoteStrategy extends BaseStrategy {


    protected RemoteStrategy(VarContext ctx) {
        super(ctx);
    }

    public Result deploy(){
        return Result.and(
            execVcs(),
            writeRevision()
        );
    }

    protected abstract Result execVcs();

    protected Result writeRevision(){
        return ctx.system.writeString(ctx.joinPath(releasePath, "REVISION"), ctx.gvar(revision));
    }
}
