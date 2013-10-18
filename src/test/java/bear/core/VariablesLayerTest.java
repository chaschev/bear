package bear.core;

import bear.plugins.AbstractContext;
import bear.session.DynamicVariable;
import bear.session.Variables;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class VariablesLayerTest {
    AbstractContext context = new AbstractContext() {
    };

    VariablesLayer layer = new VariablesLayer(context, "testLayer", null);

    private static class MyVars{
        public DynamicVariable<String> s1 = Variables.newVar("s1");
        public DynamicVariable<String> s2 = Variables.newVar("s2");
    }

    @WireFields(MyVars.class)
    private static class AutoWiring1{
        List list;
        ArrayList arrayList;
        LinkedList linkedList;

        String s1;
        String s2;
        String s3;
    }

    @WireFields
    private static class AutoWiring2{
        List list;
        ArrayList arrayList;
        LinkedList linkedList;

        String s1;
        String s2;
        String s3;
    }

    MyVars myVars = new MyVars();

    @Test
    public void testAutowires1(){
        assertThat(layer.wire(new AutoWiring1()).list).isNull();
        assertThat(layer.wire(new AutoWiring1()).arrayList).isNull();
        assertThat(layer.wire(new AutoWiring1()).linkedList).isNull();
        assertThat(layer.wire(new AutoWiring1()).s1).isEqualTo("s1");
        assertThat(layer.wire(new AutoWiring1()).s3).isNull();

        layer.putConst("MyVars.s3", "s3");
        layer.putConst("MyVars.list", new ArrayList());
        layer.putConst("MyVars.linkedList", new LinkedList());
        layer.putConst("MyVars.arrayList", new ArrayList());

        assertThat(layer.wire(new AutoWiring1()).s3).isEqualTo("s3");
        assertThat(layer.wire(new AutoWiring1()).list).isNotNull();
        assertThat(layer.wire(new AutoWiring1()).arrayList).isNotNull();
        assertThat(layer.wire(new AutoWiring1()).linkedList).isNotNull();
    }

    @Test
    public void testAutowires2(){
        assertThat(layer.wire(new AutoWiring1()).s1).isEqualTo("s1");
        assertThat(layer.wire(new AutoWiring1()).s3).isNull();

        layer.putConst("s3", "s3");
        layer.putConst("list", new ArrayList());
        layer.putConst("linkedList", new LinkedList());
        layer.putConst("arrayList", new ArrayList());

        assertThat(layer.wire(new AutoWiring1()).s3).isEqualTo("s3");
        assertThat(layer.wire(new AutoWiring1()).list).isNotNull();
        assertThat(layer.wire(new AutoWiring1()).arrayList).isNotNull();
        assertThat(layer.wire(new AutoWiring1()).linkedList).isNotNull();
    }

    @Test
    public void testAutowires3(){
        layer.putConst(List.class, new ArrayList());
        layer.putConst(LinkedList.class, new LinkedList());
        layer.putConst(ArrayList.class, new ArrayList());

        assertThat(layer.wire(new AutoWiring1()).list).isInstanceOf(ArrayList.class);
        assertThat(layer.wire(new AutoWiring1()).arrayList).isInstanceOf(ArrayList.class);
        assertThat(layer.wire(new AutoWiring1()).linkedList).isInstanceOf(LinkedList.class);
    }

    @Test
    public void testAutowires4(){
        layer.putConst(LinkedList.class, new LinkedList());
        layer.putConst(ArrayList.class, new ArrayList());

        assertThat(layer.wire(new AutoWiring1())).isNull();
    }
}
