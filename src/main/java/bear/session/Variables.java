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

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.SessionContext;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Variables {
    public static final Splitter COMMA_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();

    public static final Function<String, File> TO_FILE = new Function<String, File>() {
        public File apply(String input) {
            return new File(input);
        }
    };

    public static final Function<String, Boolean> TO_BOOLEAN = new Function<String, Boolean>() {
        public Boolean apply(String input) {
            return Boolean.valueOf(input);
        }
    };

    public static final Function<String, List<String>> COMMA_SPLIT_CONVERTER = new Function<String, List<String>>() {
        @Override
        public List<String> apply( String input) {
            return COMMA_SPLITTER.splitToList(input);
        }
    };

    public static <T extends Enum<T>> Function<String, T> toEnum(final Class<T> tClass){
        return new Function<String, T>() {
            @Override
            public T apply(String input) {
                return Enum.valueOf(tClass, input);
            }
        };
    }


    public static final Splitter LINE_SPLITTER = Splitter.on("\n").trimResults();

    protected static final Map<Class, Function<String, ?>> CONVERTERS;

    static {
        CONVERTERS = new HashMap<Class, Function<String, ?>>();
        CONVERTERS.put(File.class, TO_FILE);
        CONVERTERS.put(Boolean.class, TO_BOOLEAN);
        CONVERTERS.put(String.class, Functions.<String>identity());
    }

    public static <T> Function<String, T> getConverter(Class<T> aClass){
        return (Function<String, T>) CONVERTERS.get(aClass);
    }

    public static DynamicVariable<Boolean> not(final DynamicVariable<Boolean> b) {
        return bool("").setDynamic(new Fun<AbstractContext, Boolean>() {
            public Boolean apply(AbstractContext $) {
                return !$.varB(b);
            }
        }).temp();
    }

    public static <T> DynamicVariable<T> undefined() {
        return new DynamicVariable().defaultTo(Fun.UNDEFINED);
    }

    public static <T> DynamicVariable<T> undefined(String desc) {
        return new DynamicVariable().defaultTo(Fun.UNDEFINED).desc(desc);
    }

    public static <T, F> DynamicVariable<T> convert(final DynamicVariable<F> var, final Function<F, T> function) {
        return dynamic(new Fun<AbstractContext, T>() {
            @Override
            public T apply(AbstractContext $) {
                return function.apply($.var(var));
            }
        }).temp();
    }

    public static <T> DynamicVariable<Boolean> isEql(final DynamicVariable<T> variable, final String to) {
        return dynamic(new Fun<AbstractContext, Boolean>() {
            public Boolean apply(AbstractContext $) {
                final T v = $.var(variable);
                return v == null ? to == null : String.valueOf(v).equals(to);
            }
        }).temp();
    }

    public static <T> DynamicVariable<Boolean> isSet(final DynamicVariable<T> var) {
        return isSet(null, var);
    }

    public static <T> DynamicVariable<Boolean> isSet(String name, final DynamicVariable<T> variable) {
        return dynamic(name, "", new Fun<AbstractContext, Boolean>() {
            public Boolean apply(AbstractContext $) {
                return $.isSet(variable);
            }
        }).temp();
    }

    public static <T> DynamicVariable<T> condition(final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar) {
        return condition(null, condition, trueVar, falseVar);
    }

    public static <T> DynamicVariable<T> condition(String name, final DynamicVariable<Boolean> condition, final DynamicVariable<T> trueVar, final DynamicVariable<T> falseVar) {
        return dynamic(name, "", new Fun<AbstractContext, T>() {
            public T apply(AbstractContext $) {
                return $.varB(condition) ? $.var(trueVar) : $.var(falseVar);
            }
        }).temp();
    }

    public static <T> DynamicVariable<T> equalTo(final DynamicVariable<T> variable) {
        return dynamic("", new Fun<AbstractContext, T>() {
            public T apply(AbstractContext $) {
                return $.var(variable);
            }
        }).temp();
    }

    public static DynamicVariable<Boolean> and(final DynamicVariable... bools) {
        return bool("").setDynamic(new Fun<AbstractContext, Boolean>() {
            public Boolean apply(AbstractContext $) {
                for (DynamicVariable b : bools) {
                    if (!$.varB(b)) return false;
                }

                return true;
            }
        }).temp();
    }

    public static DynamicVariable<Boolean> or(String name, final DynamicVariable... bools) {
        return bool("").setDynamic(new Fun<AbstractContext, Boolean>() {
            public Boolean apply(AbstractContext $) {
                for (DynamicVariable b : bools) {
                    if ($.varB(b)) return true;
                }

                return false;
            }
        });
    }

    public static DynamicVariable<String> concat(final Object... varsAndStrings) {
        return dynamic(new Fun<AbstractContext, String>() {
            public String apply(AbstractContext $) {
                return Variables.concat($, varsAndStrings);
            }
        }).temp();
    }

    public static String concat(AbstractContext $, Object... varsAndStrings) {
        StringBuilder sb = new StringBuilder(128);

        for (Object o : resolveVars($, varsAndStrings)) {
            sb.append(o);
        }

        return sb.toString();
    }

    public static DynamicVariable<List<String>> split(final DynamicVariable<? extends CharSequence> str, final Splitter splitter) {
        return dynamic(new Fun<AbstractContext, List<String>>() {
            public List<String> apply(AbstractContext $) {
                return splitter.splitToList($.var(str));
            }
        }).temp();
    }

    public static DynamicVariable<String> format(final String s, final Object... varsAndStrings){
        return dynamic(new Fun<SessionContext, String>() {
            public String apply(SessionContext $) {
                return String.format(s, resolveVars($, varsAndStrings));
            }
        }).temp();
    }

    public static Object[] resolveVars(AbstractContext $, Object... varsAndStrings) {
        Object[] resolved = new Object[varsAndStrings.length];

        for (int i = 0; i < varsAndStrings.length; i++) {
            Object obj = varsAndStrings[i];

            if (obj instanceof DynamicVariable) {
                DynamicVariable var = (DynamicVariable) obj;
                resolved[i] = $.var(var);
            }else{
                resolved[i] = obj;
            }
        }

        return resolved;
    }

    public static <T> DynamicVariable<T> newVar(T _default) {
        return new DynamicVariable<T>("").defaultTo(_default);
    }

    public static <T> DynamicVariable<T> dynamic(Fun<? extends AbstractContext, T> function) {
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

    public static <T> DynamicVariable<T> dynamic(String desc, Fun<? extends AbstractContext, T> function) {
        return new DynamicVariable<T>((String) null, desc).setDynamic(function);
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc, Fun<? extends AbstractContext, T> function) {
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
