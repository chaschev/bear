package bear.plugins.groovy;

import chaschev.lang.reflect.MethodDesc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class Replacement {
    boolean visible;
    boolean field;
    String name;
    String type;
    String desc;

    public Replacement(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Replacement(Field field) {
        this.field = true;
        visible = Modifier.isPublic(field.getModifiers());
        name = field.getName();
        desc = field.getName();
        type = field.getType().getSimpleName();
    }

    public Replacement(MethodDesc method) {
        field = false;
        visible = Modifier.isPublic(method.getMethod().getModifiers());
        name = method.getName();
        type = method.getMethod().getReturnType().getSimpleName();
        desc = method.toString();
    }
}
