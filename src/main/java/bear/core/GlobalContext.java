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

import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.session.GenericUnixLocalEnvironment;
import bear.session.SystemEnvironment;
import bear.session.Variables;
import bear.task.*;
import chaschev.util.Exceptions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalContext {
    //    public static final GlobalContext INSTANCE = new GlobalContext();
    private static final GlobalContext INSTANCE = new GlobalContext();

    public final VariablesLayer variablesLayer = new VariablesLayer("global vars", null);
    public final Console console = new Console(this);
    public final Tasks tasks;

    public final Plugins plugins = new Plugins();

    public class Plugins{
        public final Map<Class<? extends Plugin>, Plugin> pluginMap = new LinkedHashMap<Class<? extends Plugin>, Plugin>();

        public boolean isSession(Class<? extends Plugin> aClass){
            return pluginMap.get(aClass) == null && pluginMap.containsKey(aClass);
        }

        public void add(Class<? extends Plugin> aClass){
            try {
                final Plugin plugin = newPluginInstance(aClass);

                pluginMap.put(aClass, plugin);
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }
        }

        public Plugin get(Class<? extends Plugin> aClass){
            final Plugin plugin = pluginMap.get(aClass);

            if(plugin == null){
                if(isSession(aClass)){
                    throw new RuntimeException("plugin " + aClass.getSimpleName() + " is a session plugin, use get(class, $)");
                }

                throw new RuntimeException("plugin " + aClass.getSimpleName() + " has not been loaded yet");
            }

            return plugin;
        }

        public <T extends Plugin> Task getSessionContext(Class<T> aClass, SessionContext $, Task parent){
            try {
                final T plugin = getPlugin(aClass);

                Task task = plugin.newSession($, parent);

                if($.var(bear.checkDependencies)){
                    DependencyResult deps = task.getDependencies().check();
                    if(!deps.ok()){
                        throw new DependencyException(deps);
                    }
                }

                return task;
            } catch (Exception e) {
                throw Exceptions.runtime(e);
            }
        }

        private <T extends Plugin> T newPluginInstance(Class<T> aClass) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            final T plugin = aClass.getConstructor(GlobalContext.class).newInstance(GlobalContext.this);

            Plugin.nameVars(plugin);

            plugin.initPlugin();

            return plugin;
        }
    }

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

    public final SystemEnvironment local;

    public final VariablesLayer localVars;

    public final SessionContext localCtx;

    public final Bear bear;

    protected Properties properties = new Properties();

    private GlobalContext() {
        bear = new Bear(this);
        local = SystemUtils.IS_OS_WINDOWS ?
            new GenericUnixLocalEnvironment("local", this) : new GenericUnixLocalEnvironment("local", this);
        localVars = SystemEnvironment.newSessionVars(this, local);

        final TaskRunner localRunner = new TaskRunner(null, this);

        localCtx = local.newCtx(localRunner);

        tasks = new Tasks(this);
    }

    public VariablesLayer gvars() {
        return variablesLayer;
    }

    public <T> T var(DynamicVariable<T> varName) {
        return variablesLayer.get(this.localCtx, varName);
    }

    public <T> T var(DynamicVariable<T> varName, T _default) {
        return variablesLayer.get(varName, _default);
    }

    public Console console() {
        return console;
    }

    public SystemEnvironment local() {
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
        return (T) plugins.get(pluginClass);
    }

    public <T extends Plugin> Task newPluginSession(Class<T> pluginClass, SessionContext $, Task parentTask) {
        return plugins.getSessionContext(pluginClass, $, parentTask);
    }

    public Set<Class<? extends Plugin>> getPluginClasses() {
        return plugins.pluginMap.keySet();
    }

    public Iterable<Plugin> getGlobalPlugins() {
        return Iterables.filter(plugins.pluginMap.values(), Predicates.notNull());
    }

    public Bear cap() {
        return bear;
    }

    public static GlobalContext getInstance() {
        return INSTANCE;
    }

    public static Tasks tasks() {
        return getInstance().tasks;
    }

    public CompositeTaskRunContext run() {
        System.out.println("running on stage...");
        return localCtx.var(bear.getStage).run();
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

        loadProperties(properties);
    }

    public void loadProperties(Properties prop) {
        this.properties = prop;

        final Enumeration<?> enumeration = prop.propertyNames();

        while (enumeration.hasMoreElements()) {
            final String name = (String) enumeration.nextElement();

            final Object v = prop.get(name);

            if (v instanceof Boolean) {
                final DynamicVariable<Boolean> value = Variables.newVar((Boolean) v).setName(name);

                variablesLayer.put(value, value);
            } else if (v instanceof String) {
                final DynamicVariable<String> value = Variables.newVar((String) v).setName(name);

                variablesLayer.put(value, value);
            } else {
                throw new UnsupportedOperationException("todo: implement for " + v.getClass());
            }
        }
    }

    public void addPlugin(Class<? extends Plugin> aClass) throws NoSuchMethodException {
        plugins.add(aClass);
    }
}