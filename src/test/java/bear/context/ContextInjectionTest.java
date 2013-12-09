package bear.context;

import bear.context.inject.InjectingVariable;
import bear.core.BearApp;
import bear.session.DynamicVariable;
import bear.session.Variables;
import org.junit.Test;

import java.util.*;

import static bear.session.Variables.dynamic;
import static java.util.Collections.singletonList;
import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ContextInjectionTest {
    public static class TestBearApp extends BearApp<TestGlobalContext> {
        public final DynamicVariable<String> s1 = Variables.newVar("s1-value");

        public final DynamicVariable<List>

            explicitListInjector = dynamic(new Fun<InjectingContext<TestSessionContext>, List>() {
            @Override
            public List apply(InjectingContext<TestSessionContext> $) {
                return singletonList("explicitList");
            }
        }),
            implicitList = dynamic(new Fun<InjectingContext<TestSessionContext>, List>() {
                @Override
                public List apply(InjectingContext<TestSessionContext> $) {
                    return singletonList("implicitList");
                }
            });
    }


    public static class TestGlobalContext extends AppGlobalContext<TestGlobalContext, TestBearApp> {

        public TestGlobalContext(TestBearApp bear) {
            super(bear);
        }
    }

    static class Foo {
        String s;

        Foo(String s) {
            this.s = s;
        }
    }

    public static class TestSessionContext extends AbstractContext {
        DynamicVariable variableFired;

        Foo foo = new Foo("foo");

        public TestSessionContext(AppGlobalContext global) {
            super(global, "test context");
        }


    }

    TestGlobalContext global = new TestGlobalContext(new TestBearApp());
    TestSessionContext $ = new TestSessionContext(global);

    @WireFields(TestBearApp.class)
    private static class AutoWiring1 {
        List list;
        ArrayList arrayList;
        LinkedList linkedList;

        String s1;
        String s2;
        String s3;
    }

    @WireFields
    private static class AutoWiring2 {
        List list;
        ArrayList arrayList;
        LinkedList linkedList;

        String s1;
        String s2;
        String s3;
    }

    private static class FieldWiring{
        Foo foo;
    }

    @WireFields(TestBearApp.class)
    private static class AutoWired3 {
        @Var("testBearApp.explicitListInjector")
        List explicitList;

        /**
         * Will be resolved implicitly by a value of var TestBearApp.implicitList
         */
        List implicitList;

        /**
         * Injected by an injector.
         */
        Map<String, String> injectedMap;

        ArrayList arrayList;
        LinkedList linkedList;

        String s1;
        String s2;
        String s3;
    }

    @Test
    public void injection_DeclaredClass(){
        global.injectors.add(new InjectingVariable<Map<String, String>>()
            .restrictDeclaringClasses(AutoWired3.class)
            .setDynamic(new Fun<InjectingContext<TestSessionContext>, Map<String, String>>() {
                @Override
                public Map<String, String> apply(InjectingContext<TestSessionContext> $) {
                    if (!Map.class.isAssignableFrom($.field.getType())) {
                        throw new UndefinedException();
                    }

                    return sampleMap("injected");
                }
            }));

        assertThat($.wire(new AutoWired3()).injectedMap.keySet()).containsExactly("injected");
    }

    @Test
    public void injection_DeclaredType(){
        global.injectors.add(new InjectingVariable<Map<String, String>>()
            .restrictTypes(Map.class)
            .setDynamic(new Fun<InjectingContext<TestSessionContext>, Map<String, String>>() {
                @Override
                public Map<String, String> apply(InjectingContext<TestSessionContext> $) {
                    return sampleMap("injected");
                }
            }));

        assertThat($.wire(new AutoWired3()).injectedMap.keySet()).containsExactly("injected");
    }

    private static Map<String, String> sampleMap(String injectedstr) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(injectedstr, injectedstr);
        return map;
    }

    @Test
    public void contextOverriding() {
        global.putConst(global.bear.implicitList, singletonList("overridden in global"));

        assertThat(global.wire(new AutoWired3()).implicitList.get(0)).isEqualTo("overridden in global");
        assertThat($.wire(new AutoWired3()).implicitList.get(0)).isEqualTo("overridden in global");

        $.put(global.bear.implicitList, new Fun<TestSessionContext, List>() {
            @Override
            public List apply(TestSessionContext $) {
                return singletonList("overridden in $");
            }
        });

        assertThat($.wire(new AutoWired3()).implicitList.get(0)).isEqualTo("overridden in $");
        assertThat(global.wire(new AutoWired3()).implicitList.get(0)).isEqualTo("overridden in global");
    }

    @Test
    public void fieldWiring(){
        assertThat($.wire(new FieldWiring()).foo).isNotNull();
    }

    @Test
    public void testAutowires1_ScopedBinding() {
        AutoWired3 autoWired3;

        autoWired3 = $.wire(new AutoWired3());

        assertThat(autoWired3.explicitList.get(0)).isEqualTo("explicitList");
        assertThat(autoWired3.implicitList.get(0)).isEqualTo("implicitList");
    }

    @Test
    public void testAutowires2_NoScope() {

    }

    @Test
    public void testAutowires3_InjectImplementation() {

    }

    @Test
    public void testAutowires4_InjectImplementation_MultipleChoice() {

    }
}
