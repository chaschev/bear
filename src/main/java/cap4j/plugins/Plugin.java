package cap4j.plugins;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.session.DynamicVariable;
import com.chaschev.chutils.util.OpenBean2;

import java.lang.reflect.Field;

/**
 * User: achaschev
 * Date: 8/13/13
 */
public class Plugin {
    public String name;
    public CapConstants cap;
    private GlobalContext global;

    public Plugin(GlobalContext global) {
        this.global = global;
        this.cap = global.cap;
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
                var.setName(className + "." + field.getName());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void init(){

    }
}
