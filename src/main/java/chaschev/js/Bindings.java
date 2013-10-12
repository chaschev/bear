package chaschev.js;

import chaschev.json.JacksonMapper;
import chaschev.json.Mapper;
import chaschev.lang.OpenBean;
import chaschev.lang.reflect.ClassDesc;
import chaschev.lang.reflect.MethodDesc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Bindings {
    private static final Logger logger = LoggerFactory.getLogger(Bindings.class);

    public ArrayList newArrayList() {
        return new ArrayList();
    }

    public static HashMap map = new HashMap(1024);

    public String foo(){
        return "foo!";
    }

    public String foo(Object param){
        return "foo, " + param + "!";
    }

    public Object newObjectArray(int size) {
        try {
            return new Object[size];
        } catch (Exception e) {
            return new ExceptionWrapper(e, "size: " + size);
        }
    }

    public Object getClassDesc(String className) {
        try {
            return OpenBean.getClassDesc(Class.forName(className));
        } catch (Exception e) {
            return new ExceptionWrapper(e, className);
        }
    }

    public Object newInstance(String className, Object... params) {
        try {
            return OpenBean.newByClass(className, params);
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + className + ", params: " + Arrays.asList(params));
        }
    }

    public Object getStaticFieldNames(String className) {
        try {
            Class<?> aClass = Class.forName(className);
            Field[] fields = OpenBean.getClassDesc(aClass).staticFields;
            String[] r = new String[fields.length];

            for (int i = 0; i < fields.length; i++) {
                r[i]  = fields[i].getName();
            }

            return r;
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + className);
        }
    }

    public Object getStaticFieldValues(String className) {
        try {
            Class<?> aClass = Class.forName(className);
            Field[] fields = OpenBean.getClassDesc(aClass).staticFields;
            Object[] r = new Object[fields.length];

            for (int i = 0; i < fields.length; i++) {
                r[i]  = fields[i].get(aClass);
            }

            return r;
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + className);
        }
    }

    public Object getMethods(String className) {
        try {
            MethodDesc[] methods = OpenBean.getClassDesc(Class.forName(className)).methods;
            String[] r = new String[methods.length];

            for (int i = 0; i < methods.length; i++) {
                r[i] = methods[i].getName();
            }

            return r;
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + className);
        }
    }

    public Object getStaticMethods(String className) {
        try {
            MethodDesc[] methods = OpenBean.getClassDesc(Class.forName(className)).staticMethods;
            String[] r = new String[methods.length];

            for (int i = 0; i < methods.length; i++) {
                r[i] = methods[i].getName();
            }

            return r;
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + className);
        }
    }

    public Object newInstanceFromDesc(ClassDesc desc, Object... params){
        try {
            return desc.getConstructorDesc(false, params).newInstance(params);
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + desc.getClass().getSimpleName());
        }
    }

    public Object callStatic(String className, String method, Object... params){
        try {
            Class<?> aClass = Class.forName(className);
            return OpenBean.getClassDesc(aClass).getStaticMethodDesc(method, false, params).invoke(aClass, params);
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + className +
                ", method: " + method +
                ", params: " + Arrays.asList(params)
            );
        }
    }

    public Object jsonSafeCall5(Object bean, String field, String method, Object param0, Object param1, Object param2, Object param3, Object param4){
        return jsonCall(bean, field, method, param0, param1, param2, param3, param4);
    }

    public Object jsonSafeCall4(Object bean, String field, String method, Object param0, Object param1, Object param2, Object param3){
        return jsonCall(bean, field, method, param0, param1, param2, param3);
    }

    public Object jsonSafeCall3(Object bean, String field, String method, Object param0, Object param1, Object param2){
        return jsonCall(bean, field, method, param0, param1, param2);
    }

    public Object jsonSafeCall2(Object bean, String field, String method, Object param0, Object param1){
        return jsonCall(bean, field, method, param0, param1);
    }

    public Object jsonSafeCall1(Object bean, String field, String method, Object param0){
        return jsonCall(bean, field, method, param0);
    }

    public Object jsonSafeCall0(Object bean, String field, String method){
        return jsonCall(bean, field, method);
    }

    public Object safeCall5(Object bean, String field, String method, Object param0, Object param1, Object param2, Object param3, Object param4){
        return call(bean, field, method, param0, param1, param2, param3, param4);
    }
    
    public Object safeCall4(Object bean, String field, String method, Object param0, Object param1, Object param2, Object param3){
        return call(bean, field, method, param0, param1, param2, param3);
    }
    
    public Object safeCall3(Object bean, String field, String method, Object param0, Object param1, Object param2){
        return call(bean, field, method, param0, param1, param2);
    }

    public Object safeCall2(Object bean, String field, String method, Object param0, Object param1){
        return call(bean, field, method, param0, param1);
    }

    public Object safeCall1(Object bean, String field, String method, Object param0){
        return call(bean, field, method, param0);
    }

    public Object safeCall0(Object bean, String field, String method){
        return call(bean, field, method);
    }

    private final Mapper mapper = new JacksonMapper();

    public Object call(Object bean, String field, String method, Object... params){
        return call(false, bean, field, method, params);
    }

    public Object jsonCall(Object bean, String field, String method, Object... params){
        return call(true, bean, field, method, params);
    }

    public Object call(boolean returnJson, Object bean, String field, String method, Object... params){
        try {
            Object delegateBean = OpenBean.getFieldValue(bean, field);
            Object invoke = OpenBean.invoke(delegateBean, method, params);
            
            return returnJson ? 
                    mapper.toJSON(invoke) : invoke;
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + bean.getClass().getSimpleName() +
                ", method: " + method +
                ", params: " + Arrays.asList(params)
            );
        }
    }

    public Object newInstance(String className, boolean strictly, Object... params) {
        try {
            return OpenBean.newByClass(className, strictly, params);
        } catch (Exception e) {
            return new ExceptionWrapper(e, "className: " + className + ", params: " + Arrays.asList(params));
        }
    }
}
