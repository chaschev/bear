package bear.context;

import bear.session.DynamicVariable;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

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
        type = varType instanceof Class ? (Class) varType :
            ((ParameterizedTypeImpl) varType).getRawType();
    }
}
