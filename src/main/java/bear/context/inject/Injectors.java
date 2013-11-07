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

package bear.context.inject;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Injectors {
    Map<Class, Class> simpleBinds = new HashMap<Class, Class>();

    final Multimap<Class, InjectingVariable> declaredExtendingIndex = HashMultimap.create();
    final Map<Class, InjectingVariable> declaredStrictIndex = new HashMap<Class, InjectingVariable>();
    final Multimap<Class, InjectingVariable> declaredSuperIndex = HashMultimap.create();

    final Multimap<Class, InjectingVariable> typeExtendingIndex = HashMultimap.create();
    final Map<Class, InjectingVariable> typeStrictIndex = new HashMap<Class, InjectingVariable>();
    final Multimap<Class, InjectingVariable> typeSuperIndex = HashMultimap.create();


    //todo optimize
    public void simpleBind(Class<?> aClass, Object obj){
        InjectingVariable var = new InjectingVariable();

        var
            .restrictTypes(aClass).defaultTo(obj);

        add(var);
    }

    public void add(InjectingVariable variable) {
        InjectingScope[] type;

        type = variable.getDeclaredClassScope();

        if (type != null) {
            for (InjectingScope scope : type) {
                for (Class aClass : scope.classes) {
                    if (scope.isSuper) {
                        declaredSuperIndex.put(aClass, variable);
                    } else {
                        declaredExtendingIndex.put(aClass, variable);
                    }
                }
            }
        }

        type = variable.getType();

        if (type != null) {
            for (InjectingScope scope : type) {
                for (Class aClass : scope.classes) {
                    if (scope.isSuper) {
                        typeSuperIndex.put(aClass, variable);
                    } else {
                        typeExtendingIndex.put(aClass, variable);
                    }
                }
            }
        }
    }

    public Iterable<InjectingVariable> findForDeclaredClass(Class<?> aClass) {
        return declaredExtendingIndex.get(aClass);
    }

    public Iterable<InjectingVariable> findForDeclaredType(Class<?> type) {
        return typeExtendingIndex.get(type);
    }


}
