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

package cap4j.session;

import cap4j.core.SessionContext;
import cap4j.core.VarFun;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.Arrays;

import static cap4j.core.Cap.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class VariableUtils {
    public static DynamicVariable<String> joinPath(final DynamicVariable<String> root, final String... folders) {
        return joinPath(null, root, folders);
    }


    public static DynamicVariable<String> joinPath(String name, final DynamicVariable<String> root, final String... folders) {
        return strVar("").setDynamic(new VarFun<String>() {
            public String apply() {
                return $.system.joinPath($.var(root), $.joinPath(folders));
            }
        });
    }

    public static DynamicVariable<String> joinPath(final DynamicVariable... folders) {
        return joinPath(null, folders);
    }

    public static DynamicVariable<String> joinPath(String name, final DynamicVariable... folders) {
        return strVar("").setDynamic(new VarFun<String>() {
            public String apply() {
                return $.system.joinPath(Iterables.transform(Arrays.asList(folders), new Function<DynamicVariable, String>() {
                    public String apply(DynamicVariable var) {
                        return $.var((DynamicVariable<String>) var);
                    }
                }));
            }
        });
    }

    public static DynamicVariable<Boolean> not(String name, final DynamicVariable<Boolean> b) {
        return bool("").setDynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return !$.varB(b);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> isEql(String name, final DynamicVariable<T> variable, final String to) {
        return dynamic(name, "", new VarFun<Boolean>() {
            public Boolean apply() {
                final T v = $.var(variable);
                return v == null ? to == v : String.valueOf(v).equals(to);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> isSet(final DynamicVariable<T> var) {
        return isSet(null, var);
    }

    public static <T> DynamicVariable<Boolean> isSet(String name, final DynamicVariable<T> variable) {
        return dynamic(name, "", new VarFun<Boolean>() {
            public Boolean apply() {
                final DynamicVariable<T> x = $.sessionVariables.getClosure(variable);

                return x != null && x.isSet();

            }
        });
    }

    public static <T> DynamicVariable<T> condition(final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar) {
        return condition(null, condition, trueVar, falseVar);
    }

    public static <T> DynamicVariable<T> condition(String name, final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar) {
        return dynamic(name, "", new VarFun<T>() {
            public T apply() {
                return $.varB(condition) ? $.var(trueVar) : $.var(falseVar);
            }
        });
    }

    public static <T> DynamicVariable<T> eql(final DynamicVariable<T> var) {
        return eql(null, var);
    }

    public static <T> DynamicVariable<T> eql(String name, final DynamicVariable<T> variable) {
        return dynamic(name, "", new VarFun<T>() {
            public T apply() {
                return $.var(variable);
            }
        });
    }

    public static DynamicVariable<Boolean> and(final DynamicVariable... bools) {
        return bool("").setDynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                for (DynamicVariable b : bools) {
                    if (!$.varB(b)) return false;
                }

                return true;
            }
        });
    }

    public static DynamicVariable<Boolean> or(String name, final DynamicVariable... bools) {
        return bool("").setDynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                for (DynamicVariable b : bools) {
                    if ($.varB(b)) return true;
                }

                return false;
            }
        });
    }

    public static DynamicVariable<String> concat(final Object... varsAndStrings) {
        return dynamic(new VarFun<String>() {
            public String apply() {
                return VariableUtils.concat($, varsAndStrings);
            }
        });
    }

    public static String concat(SessionContext $, Object... varsAndStrings) {
        StringBuilder sb = new StringBuilder(128);

        for (Object obj : varsAndStrings) {
            if (obj instanceof CharSequence) {
                sb.append(obj);
            } else if (obj instanceof DynamicVariable) {
                DynamicVariable var = (DynamicVariable) obj;
                sb.append($.var(var));
            } else {
                throw new IllegalStateException(obj + " of class " + obj.getClass().getSimpleName() + " is not supported");
            }
        }

        return sb.toString();
    }
}
