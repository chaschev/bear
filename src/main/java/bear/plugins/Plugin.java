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

import bear.console.AbstractConsole;
import bear.core.*;
import bear.task.*;

import java.util.Set;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Plugin<TASK extends Task, TASK_DEF extends TaskDef<? extends Task>> {
    public String name;
    protected String desc;

    public final Bear bear;
    protected GlobalContext global;
    protected Dependencies dependencies = new Dependencies();

    protected boolean transitiveDependency;

    Set<Plugin<Task, TaskDef<? extends Task>>> pluginDependencies;

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

    public Task<? extends TaskDef> newSession(SessionContext $, Task<TaskDef> parent){
        throw new UnsupportedOperationException("todo");
    }

    public void initPlugin() {
        if(shell != null){
            shell.init();
        }
    }

    public abstract InstallationTaskDef<? extends InstallationTask> getInstall();

    public DependencyResult checkPluginDependencies(){
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

    public AbstractConsole getConsole(){
        throw new UnsupportedOperationException("plugin does not support console");
    }

    public boolean isConsoleSupported(){
        try {
            getConsole();
            return true;
        }catch (UnsupportedOperationException e){
            return !e.getMessage().contains("plugin does not support console");
        }
    }

    protected void require(DependencyResult r, Class<? extends Plugin<Task, ? extends TaskDef>> pluginClass) {
        final Plugin<Task, ? extends TaskDef> plugin = global.getPlugin(pluginClass);

        if(plugin == null){
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

    public Set<Plugin<Task, TaskDef<? extends Task>>> getPluginDependencies() {
        return pluginDependencies;
    }

    public Set<Role> getRoles(){
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

    public final String cmdAnnotation() {
        return this.getClass().getAnnotation(Shell.class).value();
    }

    public TASK_DEF getTaskDefMixin() {
        return taskDefMixin;
    }
}
