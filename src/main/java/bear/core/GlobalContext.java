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

import bear.plugins.AbstractContext;
import bear.plugins.Plugin;
import bear.plugins.Plugins;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.plugins.sh.GenericUnixLocalEnvironmentPlugin;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.session.LocalAddress;
import bear.plugins.sh.SystemSession;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskRunner;
import bear.task.Tasks;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalContext extends AbstractContext{
    private static final Logger logger = LoggerFactory.getLogger(GlobalContext.class);

    //    public static final GlobalContext INSTANCE = new GlobalContext();
    private static final GlobalContext INSTANCE = new GlobalContext();

    public final Console console = new Console(this);
    public final Tasks tasks;

    public final Plugins plugins = new Plugins(this);

    public final ListeningExecutorService taskExecutor = listeningDecorator(new ThreadPoolExecutor(2, 32,
        5L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>()));

    public final ListeningExecutorService localExecutor = listeningDecorator(new ThreadPoolExecutor(4, 64,
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

    public final SystemSession local;

    public final SessionContext localCtx;
    public final Stage localStage;

    public final Bear bear;

    private GlobalContext() {
        super();

        layer = new VariablesLayer(this, "global layer", null);

        logger.info("adding bootstrap plugins...");
        plugins.add(GenericUnixRemoteEnvironmentPlugin.class);
        plugins.add(GenericUnixLocalEnvironmentPlugin.class);
        plugins.add(GroovyShellPlugin.class);
        plugins.build();

        bear = new Bear(this);

        final TaskRunner localRunner = new TaskRunner(null, this);

        localCtx = new SessionContext(this, new LocalAddress(), localRunner);
        local = new GenericUnixLocalEnvironmentPlugin(this).newSession(localCtx, null);
        localStage = new Stage("localStage", this).add(new LocalAddress());

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
    }

    public <T extends Plugin> T getPlugin(Class<T> pluginClass) {
//        return (T) plugins.get(pluginClass);
        return (T) getConstant(pluginClass);
    }

    public <T extends Plugin> Task<TaskDef> newPluginSession(Class<T> pluginClass, SessionContext $, Task<?> parentTask) {
        return plugins.getSessionContext(pluginClass, $, parentTask);
    }

    public Set<Class<? extends Plugin>> getPluginClasses() {
        return plugins.pluginMap.keySet();
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

    public CompositeTaskRunContext prepareToRun() {
        Stage stage = var(bear.getStage);
        logger.info("running on stage {}...", stage.name);
        return stage.prepareToRun();
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
