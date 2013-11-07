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

import bear.context.inject.InjectingVariable;
import bear.session.DynamicVariable;
import chaschev.lang.OpenBean;
import chaschev.lang.reflect.ClassDesc;
import chaschev.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;

import static bear.context.Fun.UNDEFINED;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
//todo -> extends DelegatingContext
//todo AbstractContext.getInjectingContext
public class InjectingContext<CONTEXT extends AbstractContext> extends AbstractContext {
    private static final Logger logger = LoggerFactory.getLogger(InjectingContext.class);

    Object obj;
    protected Field field;

    public InjectingContext(CONTEXT context) {
        super(context, context.name + ":inject");
        this.global = context.getGlobal();
    }

    private boolean inject(Object o, Field field, DynamicVariable<?> variable, String aCase) {
        Object result;

        try {
            synchronized (this){
                this.field = field;
                obj = o;

                result = this.var(variable);
            }
        } catch (Fun.UndefinedException e) {
            result = UNDEFINED;
        }

        if(result == UNDEFINED) return false;

        setField(field, o, result, aCase);

        return true;
    }

    @Override
    public <T> T wire(T object) {
        try {
            Class<?> objClass = object.getClass();

            boolean autowire = true;
            Class scopeClass = null;

            {
                final WireFields a = objClass.getAnnotation(WireFields.class);

                if (a != null) {
                    autowire = a.autowire();
                    scopeClass = a.value() == Void.class ? null : a.value();
                }
            }

            Field[] fields = OpenBean.getClassDesc(objClass).fields;

            String scope = scopeClass == null ? "" : DependencyInjection.shorten(scopeClass.getSimpleName()) + ".";

            Class<? extends AbstractContext> myClass = parent.getClass();

            Field[] contextFields = null;


            for (Field field : fields) {
                Var varAnnotation = field.getAnnotation(Var.class);

                if(varAnnotation != null && varAnnotation.skipWiring()){
                    continue;
                }

                Class<?> fieldClass = field.getType();

                String probableVarName = getProbableVarName(scope, field, varAnnotation);

                VariableInfo info = global.variableRegistry.get(probableVarName);

                if(info != null){
                    Object o = var(probableVarName, UNDEFINED);

                    if(o != UNDEFINED){
                        setField(field, object, o, "variable");
                        continue;
                    }
                }

                boolean autoImplThis = varAnnotation == null || varAnnotation.autoWireImpl();

                if (autoImplThis) {
                    for (InjectingVariable var : global.injectors.findForDeclaredType(fieldClass)) {
                        if(inject(object, field, var, "type injection")){
                            continue;
                        }
                    }
                }

                if (autowire) {
                    if(myClass == fieldClass){
                        setField(field, object, parent, "this");
                        continue;
                    }

                    for (InjectingVariable var : global.injectors.findForDeclaredClass(objClass)) {
                        if(inject(object, field, var, "declared class injection")){
                            continue;
                        }
                    }
                }

                if(contextFields == null){
                    contextFields = OpenBean.getClassDesc(myClass).fields;
                }

                int i = Arrays.binarySearch(contextFields, field, ClassDesc.FIELD_COMPARATOR);

                if(i>=0){
                    Field contextField = contextFields[i];

                    //don't copy fields like layer
                    if(contextField.getDeclaringClass() != AbstractContext.class){
                        setField(field, object, contextField.get(parent), "context field");
                    }
                }

            }

//            return layer.wire(object);
            return object;
        } catch (IllegalAccessException e) {
            throw Exceptions.runtime(e);
        }

    }

    private static void setField(Field field, Object object, VariablesLayer layer, String varName) {
        Object value = layer.get(varName, UNDEFINED);

        if (value != UNDEFINED) {
            setField(field, object, value, "layer");
        }
    }

    private static void setField(Field field, Object object, Object value, final String aCase) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("wiring {}.{} to {} ({})", field.getDeclaringClass().getSimpleName(), field.getName(), value, aCase);
            }

            field.set(object, value);
        } catch (IllegalArgumentException e) {
            throw Exceptions.runtime(e);
        } catch (IllegalAccessException e) {
            throw Exceptions.runtime(e);
        }
    }

    private static String concatBlank(String s1, String s2) {
        return "".equals(s1) ? s2 : s1 + s2;
    }

    @Override
    public CONTEXT getParent() {
        return (CONTEXT) parent;
    }

    private static String getProbableVarName(String scope, Field field, Var varAnnotation) {
        String varName;
        if(varAnnotation != null){
            String value = varAnnotation.value();
            if("".equals(value)){
                varName = concatBlank(scope, field.getName());
            }else
            if(value.indexOf('.') != -1){
                varName = value;
            }else{
                varName = concatBlank(scope, value);
            }
        }else{
            varName = concatBlank(scope, field.getName());
        }
        return varName;
    }
}
