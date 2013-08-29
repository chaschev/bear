package cap4j.core;

import cap4j.session.DynamicVariable;
import cap4j.session.VariableUtils;
import com.google.common.base.Function;

/**
 * User: chaschev
 * Date: 8/30/13
 */
public abstract class VarFun <T> implements Function<SessionContext, T>{
    protected DynamicVariable<T> var;

    protected SessionContext ctx;

    public abstract T apply(SessionContext ctx);

    protected String concat(Object... varsAndStrings){
        return VariableUtils.concat(ctx, varsAndStrings);
    }

    public void setVar(DynamicVariable<T> var) {
        this.var = var;
    }

    public VarFun<T> setCtx(SessionContext ctx) {
        this.ctx = ctx;
        return this;
    }
}
