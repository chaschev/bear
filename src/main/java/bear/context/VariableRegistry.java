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

import bear.session.DynamicVariable;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class VariableRegistry {
    protected final AppGlobalContext global;
    protected final Map<String, VariableInfo> variableMap = new LinkedHashMap<String, VariableInfo>();

    public Class<?> getType(String key) {
        VariableInfo info = variableMap.get(key);
        if(info == null) return null;
        return info.type;
    }

    public boolean contains(String varName) {
        return variableMap.containsKey(varName);
    }

    public VariableRegistry(AppGlobalContext global) {
        this.global = global;
    }

    public void register(DynamicVariable var, Field field) {
        variableMap.put(var.name(), new VariableInfo(var, field));
    }

    public VariableInfo get(String key) {
        return variableMap.get(key);
    }
}
