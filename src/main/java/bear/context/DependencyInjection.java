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

package bear.context;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.plugins.Plugins;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.session.DynamicVariable;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.Tasks;
import chaschev.lang.OpenBean;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DependencyInjection {
    public static void nameVars(Object obj, AppGlobalContext global) {
        final Class<?> aClass = obj.getClass();
        final String className = Plugin.shortenName(aClass.getSimpleName());
        final Field[] fields = OpenBean.getClassDesc(aClass).fields;
        final boolean isPlugin = Plugin.class.isAssignableFrom(aClass);

        try {
            for (Field field : fields) {
                if (TaskDef.class.isAssignableFrom(field.getType())) {
                    TaskDef<Object, TaskResult<?>> taskDef = (TaskDef<Object, TaskResult<?>>) field.get(obj);

                    if (taskDef != null && !taskDef.isNamed()) {
                        taskDef.setName(shortName(aClass, className, field) + "." + field.getName());
                    }

                    continue;
                }


                if (!DynamicVariable.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                final DynamicVariable var = (DynamicVariable) field.get(obj);
                Preconditions.checkNotNull(var, field.getName() + " is null!");
                var.setName(shortName(aClass, className, field) + "." + field.getName());

                global.registerVariable(var, field);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    //optimization
    private static String shortName(Class<?> aClass, String className, Field field) {
        Class<?> thisFieldClass = field.getDeclaringClass();

        return aClass == thisFieldClass ? className : Plugin.shortenName(aClass.getSimpleName());
    }

    public static void inject(Object obj, SessionContext $) {
        inject(obj, $.getGlobal(), $);
    }

    public static void inject(Object obj, GlobalContext global) {
        inject(obj, global, null);
    }

    public static void inject(Object obj, GlobalContext global, @Nullable SessionContext $) {
        Field[] fields = OpenBean.getClassDesc(obj.getClass()).fields;
        try {
            for (Field field : fields) {
                Class fieldClass = field.getType();

                if (GlobalContext.class == fieldClass) {
                    field.set(obj, global);
                } else if (Tasks.class == fieldClass) {
                    field.set(obj, global.tasks);
                } else if (Bear.class == fieldClass) {
                    field.set(obj, global.bear);
                } else if (Plugins.class == fieldClass) {
                    field.set(obj, global.plugins);
                } else if (Plugins.class == fieldClass) {
                    field.set(obj, global.plugins);
                } else if (Plugin.class.isAssignableFrom(fieldClass)) {
                    field.set(obj, global.plugin(fieldClass));
                } else {
                    String fieldName = field.getName();

                    if (SystemEnvironmentPlugin.class.isAssignableFrom(fieldClass)) {
                        if ("local".equals(fieldName)) {
                            field.set(obj, global.local);
                        } else {
                            if ($ != null) field.set(obj, $.getSys());
                        }
                    } else if (SessionContext.class.isAssignableFrom(fieldClass)) {
                        if ("localCtx".equals(fieldName)) {
                            field.set(obj, global.localCtx);
                        } else if ("$".equals(fieldName)) {
                            field.set(obj, $);
                        }
                    }
                }

            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
