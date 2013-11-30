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
import bear.plugins.DownloadPlugin;
import bear.plugins.Plugin;
import bear.plugins.Plugins;
import bear.plugins.PomPlugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.plugins.sh.GenericUnixLocalEnvironmentPlugin;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.plugins.sh.SystemSession;
import bear.session.LocalAddress;
import bear.task.SessionTaskRunner;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.Tasks;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.*;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalContext extends AppGlobalContext<GlobalContext, Bear> {
    private static final Logger logger = LoggerFactory.getLogger(GlobalContext.class);

    //    public static final GlobalContext INSTANCE = new GlobalContext();
    private static final GlobalContext INSTANCE = new GlobalContext();

    public final Console console = new Console(this);
    public final Tasks tasks;

    public final Plugins plugins = new Plugins(this);

    public final ListeningExecutorService taskExecutor = listeningDecorator(new ThreadPoolExecutor(2, 32,
        5L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>()));

    public final ListeningExecutorService localExecutor = listeningDecorator(new ThreadPoolExecutor(4, 32,
        5L, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
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

        plugins.build();

        final SessionTaskRunner localRunner = new SessionTaskRunner(null, this);

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
        taskExecutor.shutdown();
        taskExecutor.awaitTermination(10, TimeUnit.SECONDS);

        scheduler.shutdown();
        scheduler.awaitTermination(10, TimeUnit.SECONDS);
    }

    public <T extends Plugin> T getPlugin(Class<T> pluginClass) {
        return (T) plugins.get(pluginClass);
//        InjectingVariable variable = Iterables.getFirst(injectors.findForDeclaredType(pluginClass), null);
//
//        if(variable == null){
//            throw new RuntimeException("plugin not found: " + pluginClass.getSimpleName());
//        }
//
//        return (T) var(variable);
    }

    public <T extends Plugin> Task<TaskDef> newPluginSession(Class<T> pluginClass, SessionContext $, Task<?> parentTask) {
        return plugins.getSessionContext(pluginClass, $, parentTask);
    }

    public Collection<Class<? extends Plugin>> getPluginClasses() {
        return (Collection) Collections2.filter(plugins.pluginMap.keySet(), Predicates.instanceOf(Class.class));
    }

    public Iterable<Plugin> getGlobalPlugins() {
        return Iterables.filter(plugins.pluginMap.values(), Predicates.notNull());
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
