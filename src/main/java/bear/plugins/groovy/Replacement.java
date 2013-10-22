package bear.plugins.groovy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class Replacement {
    boolean visible;
    boolean field;
    String name;

    public Replacement(Field field) {
        this.field = true;
        this.visible = Modifier.isPublic(field.getModifiers());
        this.name = field.getName();
    }

    public Replacement(Method method) {
        this.field = false;
        this.visible = Modifier.isPublic(method.getModifiers());
        this.name = method.getName();
    }
}
