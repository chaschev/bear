package bear.context;

import bear.session.DynamicVariable;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class VariableInfo {
    boolean important;
    public final DynamicVariable var;
    public Class<?> type;

    public VariableInfo(DynamicVariable var, Field field) {
        this.var = var;

        Type varType = ((ParameterizedTypeImpl) field.getGenericType()).getActualTypeArguments()[0];

        this.type = _getType(varType);
    }

    private static Class _getType(Type varType) {
        Class type;

        if (varType instanceof Class) {
            type = (Class) varType;
        } else if (varType instanceof WildcardType) {
            Type[] upperBounds = ((WildcardType) varType).getUpperBounds();

            type = _getType(upperBounds[0]);
        } else {
            type = ((ParameterizedTypeImpl) varType).getRawType();
        }

        return type;
    }
}
