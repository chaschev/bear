package chaschev.js.ex;

import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Foo {
    String s;
    List list;

    public Foo(String s, List list) {
        this.s = s;
        this.list = list;
    }

    public Foo(String s) {
        this.s = s;
    }

    public Foo(List list) {
        this.list = list;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Foo{");
        sb.append("s='").append(s).append('\'');
        sb.append(", list=").append(list);
        sb.append('}');
        return sb.toString();
    }
}
