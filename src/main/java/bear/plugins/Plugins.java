package bear.plugins;

import bear.core.SessionContext;
import bear.core.GlobalContext;
import bear.plugins.graph.DirectedGraph;
import bear.task.DependencyException;
import bear.task.DependencyResult;
import bear.task.Task;
import bear.task.TaskDef;
import chaschev.lang.OpenBean;
import chaschev.util.Exceptions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Plugins {
    private static final Logger logger = LoggerFactory.getLogger(Plugins.class);

    public final Map<Object, Plugin> pluginMap = new LinkedHashMap<Object, Plugin>();

    private GlobalContext globalContext;
    private PluginBuilder pluginBuilder;

    public Plugins(GlobalContext globalContext) {
        this.globalContext = globalContext;
        pluginBuilder = new PluginBuilder(globalContext);
    }

    public void build() {
        List<Plugin> plugins = pluginBuilder.build();

        for (Plugin plugin : plugins) {
            pluginMap.put(plugin.getClass(), plugin);

            if(plugin.getShell()!=null){
                pluginMap.put(plugin.getShell().getCommandName(), plugin);
            }

            globalContext.put(plugin.getClass(), plugin);
        }

        for (Plugin plugin : plugins) {
            plugin.initPlugin();
        }
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
                Field[] fields = OpenBean.getClassDesc(pluginClass).fields;

                for (Field field : fields) {
                    Class<?> depPluginClass = field.getType();

                    if (!Plugin.class.isAssignableFrom(depPluginClass)) continue;

                    if (!this.pluginClasses.contains(depPluginClass)) {
                        pluginsToAdd.add(depPluginClass);
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

            DependencyInjection.nameVars(plugin);

            return plugin;
        }

        public List<Plugin> buildPluginTree() throws DirectedGraph.CycleResult {
            addDependantPlugins();

            List<Plugin> plugins = new ArrayList<Plugin>(pluginClasses.size());

            DirectedGraph<Plugin> pluginsGraph = new DirectedGraph<Plugin>();

            for (Class pluginClass : pluginClasses) {
                try {
                    plugins.add(newPluginInstance(pluginClass));
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
                        pluginsGraph.addEdge(
                            plugin, resolvedDep
                        );

                        try {
                            //DI
                            field.set(plugin, resolvedDep);
                        } catch (IllegalAccessException e) {
                            throw Exceptions.runtime(e);
                        }
                    }
                }
            }

            pluginsGraph.findFirstCycle();

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
        return pluginMap.get(s);
    }

    public Plugin get(Class<? extends Plugin> aClass){
        final Plugin plugin = pluginMap.get(aClass);

        if(plugin == null){
            throw new RuntimeException("plugin " + aClass.getSimpleName() + " has not been loaded yet");
        }

        return plugin;
    }

    public <T extends Plugin> Task<TaskDef> getSessionContext(Class<T> aClass, SessionContext $, Task<?> parent){
        try {
            final T plugin = globalContext.getPlugin(aClass);

            Task task = plugin.newSession($, parent);

            if($.var(globalContext.bear.checkDependencies)){
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


}
