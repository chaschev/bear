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

package bear.session;

import bear.core.Fun;
import bear.core.SessionContext;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.Arrays;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearVariables {
    public static DynamicVariable<String> joinPath(final DynamicVariable<String> root, final String... folders) {
        return joinPath(null, root, folders);
    }

    public static DynamicVariable<String> joinPath(String name, final DynamicVariable<String> root, final String... folders) {
        return Variables.strVar("").setDynamic(new Fun<String, SessionContext>() {
            public String apply(SessionContext $) {
                return $.sys.joinPath($.var(root), $.joinPath(folders));
            }
        });
    }

    public static DynamicVariable<String> joinPath(final DynamicVariable... folders) {
        return joinPath(null, folders);
    }

    public static DynamicVariable<String> joinPath(String name, final DynamicVariable... folders) {
        return Variables.strVar("").setDynamic(new Fun<String, SessionContext>() {
            public String apply(final SessionContext $) {
                return $.sys.joinPath(Iterables.transform(Arrays.asList(folders), new Function<DynamicVariable, String>() {
                    public String apply(DynamicVariable var) {
                        return $.var((DynamicVariable<String>) var);
                    }
                }));
            }
        });
    }
}
