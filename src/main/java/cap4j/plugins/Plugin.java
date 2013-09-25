package cap4j.plugins;

import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.session.DynamicVariable;
import cap4j.task.Task;
import com.chaschev.chutils.util.OpenBean2;
import com.google.common.base.Preconditions;

import java.lang.reflect.Field;

/**
 * User: achaschev
 * Date: 8/13/13
 */
public abstract class Plugin {
    public String name;
    public final Cap cap;
    protected GlobalContext global;

    public Plugin(GlobalContext global) {
        this.global = global;
        this.cap = global.cap;
        name  = getClass().getSimpleName();
    }

    public static void nameVars(Object obj){
        final Class<?> aClass = obj.getClass();
        final String className = aClass.getSimpleName();
        final Field[] fields = OpenBean2.getClassDesc(aClass).fields;

        try {
            for (Field field : fields) {
                if(!DynamicVariable.class.isAssignableFrom(field.getType())){
                    continue;
                }

                final DynamicVariable var = (DynamicVariable) field.get(obj);
                Preconditions.checkNotNull(var, field.getName() +" is null!");
                var.setName(className + "." + field.getName());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void init(){

    }

    public abstract Task getSetup();

    @Override
    public String toString() {
        return name;
    }
}
