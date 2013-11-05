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

package bear.plugins.groovy;

import chaschev.lang.reflect.MethodDesc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Replacement {
    public String name;
    public boolean visible;
    public boolean field;
    public String type;
    public String desc;
    public String snippet;

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
        snippet = name;
    }

    public Replacement(MethodDesc method) {
        field = false;
        visible = Modifier.isPublic(method.getMethod().getModifiers());
        name = method.getName();
        type = method.getMethod().getReturnType().getSimpleName();
        desc = method.toString();

        StringBuilder snippetSB = new StringBuilder(64);
        StringBuilder nameSB = new StringBuilder(64);
        snippetSB.append(name).append("(");
        nameSB.append(name).append("(");

        Class<?>[] parameterTypes = method.getMethod().getParameterTypes();

        for (int i = 0, length = parameterTypes.length; i < length; i++) {
            Class<?> aClass = parameterTypes[i];
            String simpleName = aClass.getSimpleName();

            boolean isString = aClass == String.class;

            if(isString){
                snippetSB.append('"');
            }

            snippetSB.append("${");

            snippetSB.append(i + 1).append(':').append(simpleName);

            snippetSB.append('}');

            if(isString){
                snippetSB.append('"');
            }

            nameSB.append(simpleName);

            if(i != length - 1){
                snippetSB.append(", ");
                nameSB.append(", ");
            }
        }

        snippetSB.append(")");
        nameSB.append(")");

        snippet = snippetSB.toString();
        name = nameSB.toString();
    }
}
