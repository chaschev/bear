package bear.plugins;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.task.Tasks;
import chaschev.lang.OpenBean;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DependencyInjection {
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

    public static void inject(Object obj, SessionContext $){
        inject(obj, $.getGlobal(), $);
    }

    public static void inject(Object obj, GlobalContext global){
        inject(obj, global, null);
    }

    public static void inject(Object obj, GlobalContext global, @Nullable SessionContext $){
        Field[] fields = OpenBean.getClassDesc(obj.getClass()).fields;
        try {
            for (Field field : fields) {
                Class fieldClass = field.getType();

                if(GlobalContext.class == fieldClass){
                    field.set(obj, global);
                } else
                if(Tasks.class == fieldClass){
                    field.set(obj, global.tasks);
                } else
                if(Bear.class == fieldClass){
                    field.set(obj, global.bear);
                } else
                if(Plugins.class == fieldClass){
                    field.set(obj, global.plugins);
                } else
                if(Plugins.class == fieldClass){
                    field.set(obj, global.plugins);
                } else
                if(Plugin.class.isAssignableFrom(fieldClass)){
                    field.set(obj, global.getPlugin(fieldClass));
                } else {
                    String fieldName = field.getName();

                    if(SystemEnvironmentPlugin.class.isAssignableFrom(fieldClass)){
                        if("local".equals(fieldName)){
                            field.set(obj, global.local);
                        }else{
                            if($!=null) field.set(obj, $.getSys());
                        }
                    } else
                    if(SessionContext.class.isAssignableFrom(fieldClass)){
                        if("localCtx".equals(fieldName)){
                            field.set(obj, global.localCtx);
                        }else
                        if("$".equals(fieldName)){
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
