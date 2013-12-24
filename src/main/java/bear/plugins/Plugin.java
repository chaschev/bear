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

import bear.annotations.Shell;
import bear.console.AbstractConsole;
import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.Role;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Plugin<TASK extends Task, TASK_DEF extends TaskDef> {
    public String name;
    protected String desc;

    public final Bear bear;
    protected GlobalContext global;
    protected Dependencies dependencies = new Dependencies();

    protected boolean transitiveDependency;

    Set<Plugin<Task, TaskDef>> pluginDependencies;

    protected final TASK_DEF taskDefMixin;

    protected PluginShellMode shell;


    public Plugin(GlobalContext global) {
        this(global, null);
    }

    public Plugin(GlobalContext global, TASK_DEF taskDef) {
        this.global = global;
        this.bear = global.bear;
        name = getClass().getSimpleName();
        taskDefMixin = taskDef;
    }

    public static String shortenName(String className) {
        int lastDotIndex = className.lastIndexOf('.');

        int end = className.lastIndexOf("Plugin");

        if(end == -1) end = className.length();

        return WordUtils.uncapitalize(className.substring(lastDotIndex + 1, end));
    }

    public Task<? extends TaskDef> newSession(SessionContext $, Task<TaskDef> parent) {
        throw new UnsupportedOperationException("todo");
    }

    public void initPlugin() {
        if (shell != null) {
            shell.init();
        }
    }

    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }

    public DependencyResult checkPluginDependencies() {
        return DependencyResult.OK;
    }

    @Override
    public String toString() {
        return name;
    }

    protected DependencyResult require(Class... pluginClasses) {
        final DependencyResult r = new DependencyResult(this.getClass().getSimpleName());

        for (Class pluginClass : pluginClasses) {
            require(r, pluginClass);
        }

        return r.updateResult();
    }

    public AbstractConsole getConsole() {
        throw new UnsupportedOperationException("plugin does not support console");
    }

    public boolean isConsoleSupported() {
        try {
            getConsole();
            return true;
        } catch (UnsupportedOperationException e) {
            return !e.getMessage().contains("plugin does not support console");
        }
    }

    protected void require(DependencyResult r, Class<? extends Plugin<Task, ? extends TaskDef>> pluginClass) {
        final Plugin<Task, ? extends TaskDef> plugin = global.plugin(pluginClass);

        if (plugin == null) {
            r.add(plugin.getClass().getSimpleName() + " plugin is required");
        }
    }

    public Dependencies getDependencies() {
        return dependencies;
    }

    protected final Dependencies addDependency(Dependency... dependencies) {
        return this.dependencies.addDependencies(dependencies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Plugin<Task, ? extends TaskDef> plugin = (Plugin<Task, ? extends TaskDef>) o;

        if (!name.equals(plugin.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public Set<Plugin<Task, TaskDef>> getPluginDependencies() {
        return pluginDependencies;
    }

    public Set<Role> getRoles() {
        return taskDefMixin.getRoles();
    }

    public TASK_DEF getTaskDef() {
        return taskDefMixin;
    }

    public PluginShellMode getShell() {
        return shell;
    }

    public GlobalContext getGlobal() {
        return global;
    }

    public final String getName(){
        Class<? extends Plugin> pluginClass = getClass();
        return getName(pluginClass);
    }

    public static String getName(Class<? extends Plugin> pluginClass) {
        bear.annotations.Plugin plugin = pluginClass.getAnnotation(bear.annotations.Plugin.class);

        if(plugin != null) return plugin.value();

        Shell shell = pluginClass.getAnnotation(Shell.class);

        if(shell != null) return shell.value();

        return shortenName(pluginClass.getName());
    }

    public final String cmdAnnotation() {
        return getClass().getAnnotation(Shell.class).value();
    }

    public TASK_DEF getTaskDefMixin() {
        return taskDefMixin;
    }

    protected void requireVars(DynamicVariable... vars) {
        String msg = Variables.checkSet(global, getName(), vars);

        if (msg != null) {
            LoggerFactory.getLogger("log").error(msg);
            throw new RuntimeException(msg);
        }
    }
}
