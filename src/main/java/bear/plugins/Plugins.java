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

package bear.plugins;

import bear.context.DependencyInjection;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.graph.DirectedGraph;
import bear.task.*;
import chaschev.lang.OpenBean;
import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Plugins {
    private static final Logger logger = LoggerFactory.getLogger(Plugins.class);

    protected final Map<String, Plugin<TaskDef>> pluginMap = new LinkedHashMap<String, Plugin<TaskDef>>();
    public final Map<String, Plugin> shortcutsPluginMap = new LinkedHashMap<String, Plugin>();

    private GlobalContext globalContext;
    private PluginBuilder pluginBuilder;

    public Plugins(GlobalContext globalContext) {
        this.globalContext = globalContext;
        pluginBuilder = new PluginBuilder(globalContext);
    }

    public void build() {
        List<Plugin> plugins = pluginBuilder.build();

        for (Plugin plugin : plugins) {
//            for (Plugin p : pluginMap.values()) {
//                if(p.getClass().getName().equals(plugin.getClass().getName())){
//                    logger.error("duplicate plugin: {}!!!!", plugin);
//                }
//            }

            Plugin<TaskDef> origPlugin = pluginMap.get(plugin.getClass().getName());

            if (origPlugin == null) {
                pluginMap.put(plugin.getClass().getName(), plugin);
            } else {
                logger.info("skipped loading {}@{} (there is already {}@{} loaded)", plugin, System.identityHashCode(plugin),
                    origPlugin, System.identityHashCode(origPlugin));
            }

            if(plugin.getShell()!=null){
                shortcutsPluginMap.put(plugin.getShell().getCommandName(), plugin);
            }

            globalContext.getInjectors().simpleBind(plugin.getClass(), plugin);
//            globalContext.put(plugin.getClass(), plugin);
        }

        for (Plugin plugin : plugins) {
            globalContext.wire(plugin);
            plugin.initPlugin();
        }
    }

    /**
     * Returns plugins in the order of initialization. First are the most basic.
     */
    public List<Plugin<TaskDef>> getOrderedPlugins() {

        Collection<Plugin<TaskDef>> plugins = pluginMap.values();

        return orderPlugins(plugins);
    }

    public List<Plugin<TaskDef>> orderPlugins(Collection<Plugin<TaskDef>> plugins) {
        LinkedHashSet<Plugin<TaskDef>> tempSet = new LinkedHashSet<Plugin<TaskDef>>();

        for (Plugin<TaskDef> plugin : plugins) {
            _addOrderedPlugin(plugin, tempSet);
        }

        return new ArrayList<Plugin<TaskDef>>(tempSet);
    }

    private void _addOrderedPlugin(Plugin<TaskDef> plugin, LinkedHashSet<Plugin<TaskDef>> tempSet) {
        if(tempSet.contains(plugin)) return;

        for (Plugin<TaskDef> pluginDep : plugin.getPluginDependencies()) {
            _addOrderedPlugin(pluginDep, tempSet);
        }

        tempSet.add(plugin);
    }


    protected static class PluginBuilder{
        protected List<Class<?>> pluginClasses = new ArrayList<Class<?>>();
        protected List<Class<?>> directDependencies;

        GlobalContext globalContext;

        public PluginBuilder(GlobalContext globalContext) {
            this.globalContext = globalContext;
        }

        public void add(Class<?> plugin){
            pluginClasses.add(plugin);
        }

        public void addDependantPlugins(){
            directDependencies = new ArrayList<Class<?>>(pluginClasses.subList(0, pluginClasses.size()));
            addDependantPlugins(pluginClasses, false);
        }

        private void addDependantPlugins(List<Class<?>> plugins, boolean transitive){
            List<Class<?>> pluginsToAdd = new ArrayList<Class<?>>();

            for (Class<?> pluginClass : plugins) {
                for (Field field : OpenBean.fieldsOfType(pluginClass, Plugin.class)) {
                    Class<?> type = field.getType();
                    if (!pluginClasses.contains(type)) {
                        pluginsToAdd.add(type);
                    }
                }
            }

            plugins.addAll(pluginsToAdd);

            if(!pluginsToAdd.isEmpty()){
                logger.info("adding {} dependant plugins: {}", pluginsToAdd.size(), pluginsToAdd);
                addDependantPlugins(pluginsToAdd, true);
            }
        }

        private <T extends Plugin> T newPluginInstance(Class<T> aClass) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            final T plugin = aClass.getConstructor(GlobalContext.class).newInstance(globalContext);

            DependencyInjection.nameVars(plugin, globalContext);

            return plugin;
        }

        public List<Plugin> buildPluginTree() throws DirectedGraph.CycleResult {
            addDependantPlugins();

            List<Plugin> plugins = new ArrayList<Plugin>(pluginClasses.size());

            DirectedGraph<Plugin> pluginsGraph = new DirectedGraph<Plugin>();

            for (Class pluginClass : pluginClasses) {
                try {
                    Plugin plugin = globalContext.plugins.pluginMap.get(pluginClass.getName());

                    if(plugin == null){
                        plugins.add(newPluginInstance(pluginClass));
                    }else {
                        plugins.add(plugin);
                    }
                } catch (Exception e1) {
                    throw Exceptions.runtime(e1);
                }
            }

            for (Plugin plugin : plugins) {
                for (int i = 0; i < OpenBean.getClassDesc(plugin.getClass()).fields.length; i++) {
                    Field field = OpenBean.getClassDesc(plugin.getClass()).fields[i];

                    Class<?> dependantPluginClass = field.getType();

                    if(Plugin.class.isAssignableFrom(dependantPluginClass)){
                        Plugin resolvedDep = Iterables.find(plugins, Predicates.instanceOf(dependantPluginClass));
                        try {
                            pluginsGraph.addEdge(plugin, resolvedDep);

                            //DI
                            field.set(plugin, resolvedDep);
                        }
                        catch (DirectedGraph.NoSuchNodeException e){
                            throw new RuntimeException("plugin was not loaded: " + e.node);
                        }
                        catch (IllegalAccessException e) {
                            throw Exceptions.runtime(e);
                        }
                    }
                }
            }

//            pluginsGraph.findFirstCycle();

            for (int i = directDependencies.size(); i < plugins.size(); i++) {
                plugins.get(i).transitiveDependency = true;

            }

            for (Plugin plugin : plugins) {
                plugin.pluginDependencies = pluginsGraph.edgesFrom(plugin);
            }

            return plugins;
        }

        public List<Plugin> build() throws DirectedGraph.CycleResult {
            return buildPluginTree();
        }
    }

    public void add(Class<? extends Plugin> aClass){
        try {
            pluginBuilder.add(aClass);
            // todo pluginMap.put(aClass, plugin);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public Plugin get(String s){
        return shortcutsPluginMap.get(s);
    }

    public <T extends Plugin> T get(Class<T> aClass){
        final Plugin plugin = pluginMap.get(aClass.getName());

        if(plugin == null){
            throw new RuntimeException("plugin " + aClass.getSimpleName() + " has not been loaded yet");
        }

        return (T) plugin;
    }

    @Nonnull
    public <T extends Plugin> Optional<T> getOptional(Class<T> pluginClass) {
        return Optional.fromNullable((T)pluginMap.get(pluginClass.getName()));
    }

    public <T extends Plugin> Task<Object, TaskResult> getSessionContext(Class<T> aClass, SessionContext $, Task<Object, TaskResult> parent){
        try {
            final T plugin = globalContext.plugin(aClass);

            return newSession(plugin, $, parent);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public <T extends Plugin> Task<Object, TaskResult> newSession(T plugin, SessionContext $, Task<Object, TaskResult> parent) {
        Task task = plugin.newSession($, parent);

        task.wire($);

        if($.var(globalContext.bear.checkDependencies)){
            DependencyResult deps = task.getDependencies().check();
            if(!deps.ok()){
                throw new DependencyException(deps);
            }
        }

        return task;
    }

    public Map<String, Plugin<TaskDef>> getPluginMap() {
        return pluginMap;
    }
}
