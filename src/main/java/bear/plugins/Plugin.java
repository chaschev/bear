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

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.task.*;
import bear.console.AbstractConsole;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import chaschev.lang.OpenBean;
import com.google.common.base.Preconditions;

import java.lang.reflect.Field;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Plugin {
    public String name;
    public final Bear bear;
    protected GlobalContext global;
    protected Dependencies dependencies = new Dependencies();

    public Plugin(GlobalContext global) {
        this.global = global;
        this.bear = global.bear;
        name = getClass().getSimpleName();
    }

    public Task newSession(SessionContext $, Task parent){
        throw new UnsupportedOperationException("todo");
    }

    public static void nameVars(Object obj) {
        final Class<?> aClass = obj.getClass();
        final String className = aClass.getSimpleName();
        final Field[] fields = OpenBean.getClassDesc(aClass).fields;

        try {
            for (Field field : fields) {
                if (!DynamicVariable.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                final DynamicVariable var = (DynamicVariable) field.get(obj);
                Preconditions.checkNotNull(var, field.getName() + " is null!");
                var.setName(className + "." + field.getName());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void initPlugin() {

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

    protected void require(DependencyResult r, Class<? extends Plugin> pluginClass) {
        final Plugin plugin = global.getPlugin(pluginClass);

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
}
