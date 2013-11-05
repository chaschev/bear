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

package chaschev.js.ex;

import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Foo {
    String s;
    String s2;
    List list;

    public static final String CONSTANT = "FOO CONST";

    public Foo(String s, List list) {
        this.s = s;
        this.list = list;
    }

    public Foo(String s) {
        this.s = s;
    }

    public Foo(String s, String s2) {
        this.s = s;
        this.s2 = s2;
    }

    public Foo(String s, String s2, List list) {
        this.s = s;
        this.s2 = s2;
        this.list = list;
    }

    public Foo(List list) {
        this.list = list;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Foo{");
        sb.append("s='").append(s).append('\'');
        sb.append(", s2='").append(s2).append('\'');
        sb.append(", list=").append(list);
        sb.append('}');
        return sb.toString();
    }

    public static String staticFoo(String s){
        System.out.println("static foo: " + s);
        return "static foo(" +  s + ")";
    }
}
