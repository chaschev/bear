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
