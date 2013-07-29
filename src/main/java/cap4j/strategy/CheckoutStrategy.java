package cap4j.strategy;

import cap4j.scm.BaseScm;
import cap4j.session.Result;

import static cap4j.CapConstants.*;

/**
 * User: chaschev
 * Date: 7/28/13
 */
public class CheckoutStrategy extends RemoteStrategy {
    @Override
    protected Result execVcs() {
        return ctx.system.runVCS(
            ctx.gvar(vcs).checkout(
                ctx.gvar(revision),
                ctx.gvar(releasePath),
                BaseScm.emptyParams()
            )).result;
    }
}
