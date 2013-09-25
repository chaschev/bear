package cap4j.core;

import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;
import cap4j.session.GenericUnixLocalEnvironment;
import cap4j.session.SystemEnvironment;
import cap4j.task.Tasks;
import com.chaschev.chutils.util.Exceptions;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class GlobalContext {
//    public static final GlobalContext INSTANCE = new GlobalContext();
    private static final GlobalContext INSTANCE = new GlobalContext();

    public final Variables variables = new Variables("global vars", null);
    public final Console console = new Console(this);
    public final Tasks tasks;

    public final Map<Class<? extends Plugin>, Plugin> pluginMap = new HashMap<Class<? extends Plugin>, Plugin>();

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

    public final SystemEnvironment local;

    public final Variables localVars;

    public final SessionContext localCtx;
    public final Cap cap;
    protected Properties properties = new Properties();

    private GlobalContext() {
        cap = new Cap(this);
        local = SystemUtils.IS_OS_WINDOWS ?
            new GenericUnixLocalEnvironment("local", this) : new GenericUnixLocalEnvironment("local", this);
        localVars = SystemEnvironment.newSessionVars(this, local);
        localCtx = new SessionContext(this, local);
        tasks = new Tasks(this);

    }

//    protected GlobalContext() {
//
//    }

    public Variables gvars(){
        return variables;
    }

    public <T> T var(DynamicVariable<T> varName){
        return variables.get(this.localCtx, varName);
    }

    public <T> T var(DynamicVariable<T> varName, T _default){
        return variables.get(varName, _default);
    }

    public Console console(){
        return console;
    }

    public SystemEnvironment local(){
        return local;
    }

    public SessionContext localCtx(){
        return getInstance().localCtx;
    }

    public void shutdown() throws InterruptedException {
        taskExecutor.shutdown();
        taskExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    public <T extends Plugin> T getPlugin(Class<T> pluginClass) {
        final T plugin = (T) pluginMap.get(pluginClass);

        Preconditions.checkNotNull(plugin, "plugin " + pluginClass.getSimpleName() + " has not been loaded yet");

        return plugin;
    }

    public Collection<Plugin> getPlugins() {
        return pluginMap.values();
    }

    public static <T extends Plugin> T plugin(Class<T> pluginClass) {
        return getInstance().getPlugin(pluginClass);
    }

    public Cap cap(){
        return cap;
    }

    public static GlobalContext getInstance() {
        return INSTANCE;
    }

    public static Tasks tasks(){
        return getInstance().tasks;
    }

    public void run() {
        System.out.println("running on stage...");
        localCtx.var(cap.getStage).run();
    }

    public String getProperty(String s) {
        return properties.getProperty(s);
    }

    public void loadProperties(File file) {
        try {
            final FileInputStream fis = new FileInputStream(file);
            loadProperties(fis);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }

    }

    public void loadProperties(InputStream is) throws IOException {
        properties.load(is);

        final Enumeration<?> enumeration = properties.propertyNames();

        while(enumeration.hasMoreElements()){
            final String name = (String) enumeration.nextElement();

            final Object v = properties.get(name);

            if (v instanceof Boolean) {
                final DynamicVariable<Boolean> value = Cap.newVar((Boolean) v).setName(name);

                variables.put(value, value);
            }else if (v instanceof String) {
                final DynamicVariable<String> value = Cap.newVar((String) v).setName(name);

                variables.put(value, value);
            }else{
                throw new UnsupportedOperationException("todo: implement for " + v.getClass());
            }
        }
    }
}
