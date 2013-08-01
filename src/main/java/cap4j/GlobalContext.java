package cap4j;

import cap4j.session.DynamicVariable;
import cap4j.session.GenericUnixLocalEnvironment;
import cap4j.session.SystemEnvironment;
import org.apache.commons.lang3.SystemUtils;

import java.util.concurrent.*;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class GlobalContext {
    public static GlobalContext INSTANCE;
    public final Variables variables = new Variables("global vars", null);
    public final Console console = new Console();

    public final ExecutorService taskExecutor = new ThreadPoolExecutor(2, 32,
        5L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>());

    public final ExecutorService localExecutor = new ThreadPoolExecutor(4, 64,
        5L, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }
        });

    public final SystemEnvironment local = SystemUtils.IS_OS_WINDOWS ?
        new GenericUnixLocalEnvironment("local") : new GenericUnixLocalEnvironment("local");

    public final Variables localVars = Stage.newSessionVars(this, local);

    public final VarContext localCtx = new VarContext(localVars, local);

    protected GlobalContext() {

    }

    public static Variables gvars(){
        return INSTANCE.variables;
    }

    public static String var(Nameable<String> varName){
        return INSTANCE.variables.get(varName, null);
    }

    public static <T> T var(DynamicVariable<T> varName, T _default){
        return INSTANCE.variables.get(varName, _default);
    }

    public static Console console(){
        return INSTANCE.console;
    }

    public static SystemEnvironment local(){
        return INSTANCE.local;
    }


}
