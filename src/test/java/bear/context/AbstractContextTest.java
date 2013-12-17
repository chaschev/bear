package bear.context;

import bear.core.BearApp;
import bear.session.DynamicVariable;
import chaschev.util.Exceptions;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static bear.session.Variables.*;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;
import static org.fest.assertions.api.Assertions.failBecauseExceptionWasNotThrown;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class AbstractContextTest extends HavingContext<AbstractContextTest, AbstractContextTest.TestContext> {

    private TestBearApp bear = new TestBearApp();
    private AppGlobalContext<AppGlobalContext, TestBearApp> global = new AppGlobalContext<AppGlobalContext, TestBearApp>(bear);

    public AbstractContextTest() {
        super(null);

        $ = new TestContext(global, "test session");
    }

    public static class TestBearApp extends BearApp<AppGlobalContext>{
        final DynamicVariable<Integer> computationTime = undefined();

        final DynamicVariable<String>
            var1 = newVar("var1"),
            varUndefined = undefined(),
            varDynamic = dynamic(new Fun<AbstractContext, String>() {
                @Override
                public String apply(AbstractContext $) {
                    return "dynamic";
                }
            }),
            varDynamicConcat = concat(var1, "/", varDynamic),

            varMemoized = dynamic(new Fun<AbstractContext, String>() {
                @Override
                public String apply(AbstractContext $) {
                    try {
                        Integer millis = $.var(computationTime);
                        if(millis > 0){
                            Thread.sleep(millis);
                        }
                        return $.getName();
                    } catch (InterruptedException e) {
                        throw Exceptions.runtime(e);
                    }
                }
            }).memoizeIn(AppGlobalContext.class);

    }

    public static class TestContext extends AbstractContext{
        public TestContext(AbstractContext parentContext, String name) {
            super(parentContext, name);
        }
    }

    @Test
    public void testComposition() throws Exception {
        assertThat($(bear.varDynamicConcat)).isEqualTo("var1/dynamic");
    }

    @Test
    public void testCompositionOverrides() throws Exception {
        // dynamic
        $.putConst(bear.varDynamic, "$");

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("var1/$");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");

        $.removeConst(bear.varDynamic);

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");

        global.putConst(bear.varDynamic, "global");

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("var1/global");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("var1/global");

        global.removeConst(bear.varDynamic);

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");

        // now same with static

        $.putConst(bear.var1, "$");

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("$/dynamic");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");

        $.removeConst(bear.var1);

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");

        global.putConst(bear.var1, "global");

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("global/dynamic");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("global/dynamic");

        global.removeConst(bear.var1);

        assertThat($.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");
        assertThat(global.var(bear.varDynamicConcat)).isEqualTo("var1/dynamic");
    }

    @Test
    public void testFreezes() throws Exception {
        // no freezes! :-)
    }

    @Test
    public void testMemoization() throws Exception {
        //computation is slow for the first var, however the second should wait for it to finish

        final TestContext $1 = new TestContext(global, "test session 1");
        final TestContext $2 = new TestContext(global, "test session 2");

        $1.putConst(bear.computationTime, 300);
        $2.putConst(bear.computationTime, 0);

        final String[] var1 = new String[1];
        final String[] var2 = new String[1];

        final CountDownLatch latch = new CountDownLatch(1);

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.countDown();
                    var1[0] = $1.var(bear.varMemoized);
                } catch (Exception e) {
                    fail(e.toString(), e);
                }
            }
        }, "t1");

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    var2[0] = $2.var(bear.varMemoized);
                } catch (Exception e) {
                    fail(e.toString(), e);
                }
            }
        }, "t2");

        t1.start();
        latch.await();
        t2.start();
        t1.join();
        t2.join();

        assertThat(var1[0]).isEqualTo("test session 1");
        assertThat(var2[0]).isEqualTo("test session 1");

    }

    @Test
    public void testMemoizationInSession() throws Exception {
        //computation is slow for the first var, however the second should wait for it to finish

        bear.varMemoized.memoizeIn(TestContext.class);

        final TestContext $1 = new TestContext(global, "test session 1");
        final TestContext $2 = new TestContext(global, "test session 2");

        $1.putConst(bear.computationTime, 300);
        $2.putConst(bear.computationTime, 0);

        final String[] var1 = new String[1];
        final String[] var2 = new String[1];

        final CountDownLatch latch = new CountDownLatch(1);

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.countDown();
                    var1[0] = $1.var(bear.varMemoized);
                } catch (Exception e) {
                    fail(e.toString(), e);
                }
            }
        }, "t1");

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    var2[0] = $2.var(bear.varMemoized);
                } catch (Exception e) {
                    fail(e.toString(), e);
                }
            }
        }, "t2");

        t1.start();
        latch.await();
        t2.start();
        t1.join();
        t2.join();

        assertThat(var1[0]).isEqualTo("test session 1");
        assertThat(var2[0]).isEqualTo("test session 2");

    }


    @Test
    public void testDefaultValue() throws Exception {
        assertVarValue(bear.var1, "var1");
    }

    @Test
    public void testGlobalOverrides() throws Exception {
        testGlobalOverrides(bear.var1, "var1");
        testGlobalOverrides(bear.varDynamic, "dynamic");
    }

    private void testGlobalOverrides(DynamicVariable<String> var, String defaultV) {
        global.putConst(var, "overridden");

        assertThat($(var)).isEqualTo("overridden");
        assertThat(global.var(var)).isEqualTo("overridden");

        global.removeConst(var);

        assertVarValue(var, defaultV);
    }

    @Test
    public void testSessionOverrides() throws Exception {
        testSessionOverrides(bear.var1, "var1");
        testSessionOverrides(bear.varDynamic, "dynamic");
    }

    private void testSessionOverrides(DynamicVariable<String> var, String expected) {
        $.putConst(var, "$");

        assertThat($(var)).isEqualTo("$");
        assertThat(global.var(var)).isEqualTo(expected);

        $.removeConst(var);

        assertVarValue(var, expected);
    }

    @Test
    public void testUndefined() throws Exception {
        try {
            $(bear.varUndefined);
            failBecauseExceptionWasNotThrown(Fun.UndefinedException.class);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(Fun.UndefinedException.class);
        }
    }

    private void assertVarValue(DynamicVariable<String> var, String expected) {
        assertThat($(var)).isEqualTo(expected);
        assertThat(global.var(var)).isEqualTo(expected);
    }


}
