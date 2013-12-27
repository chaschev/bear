package examples.demo;

import org.junit.Test;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

public class UnitTest {
    public UnitTest() {
        System.out.println("new instance");
    }

    @Test
    public void foo1(){
        System.out.println("foo1");
    }

    @Test
    public void foo2(){
        System.out.println("foo2");
    }
}
