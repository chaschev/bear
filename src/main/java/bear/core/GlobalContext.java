/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.core;

import bear.context.AppGlobalContext;
import bear.main.CompileManager;
import bear.plugins.DownloadPlugin;
import bear.plugins.Plugin;
import bear.plugins.Plugins;
import bear.plugins.PomPlugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.plugins.sh.GenericUnixLocalEnvironmentPlugin;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.plugins.sh.SystemSession;
import bear.session.LocalAddress;
import bear.task.*;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalContext extends AppGlobalContext<GlobalContext, Bear> {
    private static final Logger logger = LoggerFactory.getLogger(GlobalContext.class);

    //    public static final GlobalContext INSTANCE = new GlobalContext();
    private static GlobalContext INSTANCE = new GlobalContext();

    public final Console console = new Console(this);
    public final Tasks tasks;

    public final Plugins plugins = new Plugins(this);

        public <T extends Plugin> Optional<T> pluginOfInstance(Class<? extends T> pluginClass) {
            Class result = null;

            for (Plugin<Task, TaskDef> plugin : getPlugins()) {
//                    Class<?> canonical = Class.forName(plugin.getClass().getName());
                Class<? extends Plugin> aClass = plugin.getClass();
                if(pluginClass.isAssignableFrom(aClass)){
                    result = aClass;
                    break;
                }
            }

            if(result == null) return Optional.absent();

            return (Optional<T>) plugins.getOptional(result);
        }

    public static class ProjectRegistry{
        CompileManager manager;
    }


    public static class AwareThread extends Thread{
        private Exception interruptedAt;
        private String interruptedBy;

        public AwareThread() {
        }

        public AwareThread(Runnable target) {
            super(target);
        }

        public AwareThread(String name) {
            super(name);
        }

        public AwareThread(Runnable target, String name) {
            super(target, name);
        }

        @Override
        public void interrupt() {
            interruptedAt = new Exception();
            interruptedBy = Thread.currentThread().getName();
            super.interrupt();
        }

        public Exception getInterruptedAt() {
            return interruptedAt;
        }

        public String getInterruptedBy() {
            return interruptedBy;
        }

        public boolean wasInterrupted(){
            return interruptedAt != null;
        }
    }

    public final ListeningExecutorService localExecutor = listeningDecorator(new ThreadPoolExecutor(4, 32,
        5L, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final AwareThread thread = new AwareThread(r);
                thread.setDaemon(true);
                return thread;
            }
        }));

    public final ListeningExecutorService sessionsExecutor = listeningDecorator(new ThreadPoolExecutor(4, 32,
        5L, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final AwareThread thread = new AwareThread(r);
                thread.setDaemon(true);
                return thread;
            }
        }));

    public final ListeningScheduledExecutorService scheduler = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(3));

    public final SystemSession local;

    public final SessionContext localCtx;
    public final Stage localStage;
    public GlobalTaskRunner currentGlobalRunner;

    GlobalContext() {
        super(new Bear());

        logger.info("adding bootstrap plugins...");

        plugins.add(GenericUnixRemoteEnvironmentPlugin.class);
        plugins.add(GenericUnixLocalEnvironmentPlugin.class);
        plugins.add(GroovyShellPlugin.class);
        plugins.add(PomPlugin.class);
        plugins.add(DownloadPlugin.class);
//        plugins.add(ReleasesPlugin.class);

        plugins.build();

        final SessionRunner localRunner = new SessionRunner(null, this);

        localCtx = new SessionContext(this, new LocalAddress(), localRunner);
        local = new GenericUnixLocalEnvironmentPlugin(this).newSession(localCtx, null);
        localStage = new Stage("localStage").add(new LocalAddress());

        tasks = new Tasks(this);
    }

    public Console console() {
        return console;
    }

    public SystemSession local() {
        return local;
    }

    public SessionContext localCtx() {
        return getInstance().localCtx;
    }

    public void shutdown() throws InterruptedException {
        scheduler.shutdown();
        sessionsExecutor.shutdown();

        if(!sessionsExecutor.awaitTermination(5, TimeUnit.SECONDS)){
            sessionsExecutor.shutdownNow();
        }

        if(!scheduler.awaitTermination(3, TimeUnit.SECONDS)){
            scheduler.shutdownNow();
        }
    }

    @Nonnull
    public <T extends Plugin> Optional<T> getPlugin(Class<T> pluginClass) {
        return plugins.getOptional(pluginClass);
    }

    public <T extends Plugin> T plugin(Class<T> pluginClass) {
        return plugins.get(pluginClass);
    }

    public <T extends Plugin> Task<Object, TaskResult> newPluginSession(Class<T> pluginClass, SessionContext $, Task<Object, TaskResult> parentTask) {
        return plugins.getSessionContext(pluginClass, $, parentTask);
    }

    public Set<String> getPluginClasses() {
        return plugins.getPluginMap().keySet();
    }

    public Collection<Plugin<Task, TaskDef>> getPlugins() {
        return plugins.getPluginMap().values();
    }

    public List<Plugin<Task, TaskDef>> getOrderedPlugins() {
        return plugins.getOrderedPlugins();
    }

    public static GlobalContext getInstance() {
        return INSTANCE;
    }

    public static Tasks tasks() {
        return getInstance().tasks;
    }

    public void addPlugin(Class<? extends Plugin> aClass) {
        plugins.add(aClass);
    }

    public void initPlugins() {
        logger.info("initializing plugins...");
        plugins.build();
    }

    @Override
    public GlobalContext getGlobal() {
        return this;
    }
}
