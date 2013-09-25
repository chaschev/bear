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

package cap4j.core;

import cap4j.session.DynamicVariable;
import cap4j.session.VariableUtils;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class VarFun<T> {
    protected DynamicVariable<T> var;

    protected SessionContext $;

    public abstract T apply();

    protected String concat(Object... varsAndStrings) {
        return VariableUtils.concat($, varsAndStrings);
    }

    public void setVar(DynamicVariable<T> var) {
        this.var = var;
    }

    public VarFun<T> set$(SessionContext $) {
        this.$ = $;
        return this;
    }
}
