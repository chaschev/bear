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
import bear.core.VarFun;
import bear.plugins.AbstractContext;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Variables {


    public static DynamicVariable<Boolean> not(final DynamicVariable<Boolean> b) {
        return bool("").setDynamic(new Fun<Boolean, AbstractContext>() {
            public Boolean apply(AbstractContext $) {
                return !$.varB(b);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> isEql(String name, final DynamicVariable<T> variable, final String to) {
        return dynamic(name, "", new Fun<Boolean, AbstractContext>() {
            public Boolean apply(AbstractContext $) {
                final T v = $.var(variable);
                return v == null ? to == null : String.valueOf(v).equals(to);
            }
        });
    }

    public static <T> DynamicVariable<Boolean> isSet(final DynamicVariable<T> var) {
        return isSet(null, var);
    }

    public static <T> DynamicVariable<Boolean> isSet(String name, final DynamicVariable<T> variable) {
        return dynamic(name, "", new Fun<Boolean, AbstractContext>() {
            public Boolean apply(AbstractContext $) {
                return $.isSet(variable);
            }
        });
    }

    public static <T> DynamicVariable<T> condition(final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar) {
        return condition(null, condition, trueVar, falseVar);
    }

    public static <T> DynamicVariable<T> condition(String name, final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar) {
        return dynamic(name, "", new Fun<T, AbstractContext>() {
            public T apply(AbstractContext $) {
                return $.varB(condition) ? $.var(trueVar) : $.var(falseVar);
            }
        });
    }

    public static <T> DynamicVariable<T> equalTo(final DynamicVariable<T> variable) {
        return dynamic("", new Fun<T, AbstractContext>() {
            public T apply(AbstractContext $) {
                return $.var(variable);
            }
        });
    }

    public static DynamicVariable<Boolean> and(final DynamicVariable... bools) {
        return bool("").setDynamic(new Fun<Boolean, AbstractContext>() {
            public Boolean apply(AbstractContext $) {
                for (DynamicVariable b : bools) {
                    if (!$.varB(b)) return false;
                }

                return true;
            }
        });
    }

    public static DynamicVariable<Boolean> or(String name, final DynamicVariable... bools) {
        return bool("").setDynamic(new Fun<Boolean, AbstractContext>() {
            public Boolean apply(AbstractContext $) {
                for (DynamicVariable b : bools) {
                    if ($.varB(b)) return true;
                }

                return false;
            }
        });
    }

    public static DynamicVariable<String> concat(final Object... varsAndStrings) {
        return dynamic(new Fun<String, AbstractContext>() {
            public String apply(AbstractContext $) {
                return Variables.concat($, varsAndStrings);
            }
        });
    }

    public static String concat(AbstractContext $, Object... varsAndStrings) {
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

    @Deprecated
    public static <T> DynamicVariable<T> dynamicNotSet() {
        return dynamicNotSet(null, "");
    }

    @Deprecated
    public static <T> DynamicVariable<T> dynamicNotSet(String desc) {
        return dynamicNotSet(null, desc);
    }

    @Deprecated
    public static <T> DynamicVariable<T> dynamicNotSet(final String name, String desc) {
        return dynamic(name, desc, new VarFun<T, AbstractContext>() {
            public T apply(AbstractContext $) {
                throw new VarNotSetException(var);
            }
        });
    }

    public static <T> DynamicVariable<T> newVar(T _default) {
        return new DynamicVariable<T>("").defaultTo(_default);
    }

    public static <T> DynamicVariable<T> dynamic(Fun<T, ? extends AbstractContext> function) {
        return dynamic(null, "", function);
    }

    public static <T> DynamicVariable<T> dynamic(String desc) {
        return dynamic(null, desc);
    }

    public static <T> DynamicVariable<T> dynamic(Class<T> type) {
        return new DynamicVariable<T>();
    }

    static <T> DynamicVariable<T> dynamic(String name, String desc) {
        return new DynamicVariable<T>(name, desc);
    }

    public static <T> DynamicVariable<T> dynamic(String desc, Fun<T, ? extends AbstractContext> function) {
        return new DynamicVariable<T>((String) null, desc).setDynamic(function);
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc, Fun<T, ? extends AbstractContext> function) {
        return new DynamicVariable<T>(name, desc).setDynamic(function);
    }

    public static DynamicVariable<String> enumConstant(String name, final String desc, final String... options) {
        return new DynamicVariable<String>(name, desc) {
            @Override
            public void validate(String value) {
                if (!ArrayUtils.contains(options, value)) {
                    Preconditions.checkArgument(false, ":" + name +
                        " must be one of: " + Arrays.asList(options));
                }
            }
        };
    }

    public static DynamicVariable<String> strVar() {
        return strVar("");
    }

    public static DynamicVariable<String> strVar(String desc) {
        return new DynamicVariable<String>((String) null, desc);
    }

    public static DynamicVariable<Boolean> bool() {
        return new DynamicVariable<Boolean>(null);
    }

    public static DynamicVariable<Boolean> bool(String desc) {
        return new DynamicVariable<Boolean>(desc);
    }

    public static String checkSet(AbstractContext $, final String actor, DynamicVariable... vars){
        StringBuilder sb = new StringBuilder(256);

        for (DynamicVariable var : vars) {
            if(!$.isSet(var)){
                sb.append(" :").append(var.name);
                if(!StringUtils.isBlank(var.desc)){
                    sb.append(" - ").append(var.desc);
                }
                sb.append("\n");
            }
        }

        return sb.length() == 0 ? null : "(" + actor + "): you need to set variables:\n" + sb.toString();
    }
}
