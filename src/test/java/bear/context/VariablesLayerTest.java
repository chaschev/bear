package bear.context;

import bear.core.GlobalContext;
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
    VariablesLayer layer = new VariablesLayer("testLayer", null);

    AbstractContext context = new AbstractContext(layer) {
    };

    {
        layer.$ = context;
    }

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

    {
        DependencyInjection.nameVars(myVars, GlobalContext.getInstance());
        layer.addVariables(myVars);
    }

    @Test
    public void testAutowires1_ScopedBinding(){
        assertThat(layer.wire(new AutoWiring1()).s1).isEqualTo("s1");
        assertThat(layer.wire(new AutoWiring1()).list).isNull();
        assertThat(layer.wire(new AutoWiring1()).arrayList).isNull();
        assertThat(layer.wire(new AutoWiring1()).linkedList).isNull();
        assertThat(layer.wire(new AutoWiring1()).s3).isNull();

        layer.putConst("myVars.s3", "s3");
        layer.putConstObj("myVars.list", new ArrayList());
        layer.putConstObj("myVars.linkedList", new LinkedList());
        layer.putConstObj("myVars.arrayList", new ArrayList());

        assertThat(layer.wire(new AutoWiring1()).s3).isEqualTo("s3");
        assertThat(layer.wire(new AutoWiring1()).list).isNotNull();
        assertThat(layer.wire(new AutoWiring1()).arrayList).isNotNull();
        assertThat(layer.wire(new AutoWiring1()).linkedList).isNotNull();
    }

    @Test
    public void testAutowires2_NoScope(){
        assertThat(layer.wire(new AutoWiring2()).s1).isNull();
        assertThat(layer.wire(new AutoWiring2()).s3).isNull();
        assertThat(layer.wire(new AutoWiring2()).list).isNull();

        layer.putConst("s3", "s3");
        layer.putConstObj("list", new ArrayList());
        layer.putConstObj("linkedList", new LinkedList());
        layer.putConstObj("arrayList", new ArrayList());

        assertThat(layer.wire(new AutoWiring2()).s3).isEqualTo("s3");
        assertThat(layer.wire(new AutoWiring2()).list).isNotNull();
        assertThat(layer.wire(new AutoWiring2()).arrayList).isNotNull();
        assertThat(layer.wire(new AutoWiring2()).linkedList).isNotNull();
    }

    @Test
    public void testAutowires3_InjectImplementation(){

        layer.putConstObj(List.class, new ArrayList());
        layer.putConstObj(LinkedList.class, new LinkedList());
        layer.putConstObj(ArrayList.class, new ArrayList());

        assertThat(layer.wire(new AutoWiring2()).list).isInstanceOf(ArrayList.class);
        assertThat(layer.wire(new AutoWiring2()).arrayList).isInstanceOf(ArrayList.class);
        assertThat(layer.wire(new AutoWiring2()).linkedList).isInstanceOf(LinkedList.class);
    }

    @Test
    public void testAutowires4_InjectImplementation_MultipleChoice(){
        layer.putConstObj(LinkedList.class, new LinkedList());
        layer.putConstObj(ArrayList.class, new ArrayList());

        assertThat(layer.wire(new AutoWiring2()).list).isNull();
        assertThat(layer.wire(new AutoWiring2()).arrayList).isNotNull();
    }
}
