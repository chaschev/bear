package bear.main.phaser;

import chaschev.util.Exceptions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class OnceEnteredCallableTest {
    private static final Logger logger = LoggerFactory.getLogger(OnceEnteredCallableTest.class);
    @Test
    public void test1() throws Exception {
        Random random = new Random();

        for(int i = 0;i<50;i++){
            int[] theGuy = new int[1];
            runExperiment(new int[]{0, 0}, theGuy);
        }

        for(int i = 0;i<3;i++){
            int[] theGuy = new int[1];
            int slowGuy = random.nextInt(2);

            int[] sleep = {0, 0};
            sleep[slowGuy] = 300;

            runExperiment(sleep, theGuy);

            assertThat(theGuy[0]).isEqualTo(1 - slowGuy);
        }
    }

    private static void runExperiment(final int[] sleep, final int[] theGuy) throws InterruptedException {
        final AtomicInteger entries = new AtomicInteger();

        final OnceEnteredCallable<String> callable = new OnceEnteredCallable<String>();
        final Thread[] threads = new Thread[2];

        for(int i = 0;i<2;i++){
            final int finalI = i;

            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(sleep[finalI]);

                        Future<String> fut = callable.runOnce(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                theGuy[0] = finalI;
                                entries.incrementAndGet();
                                return finalI + ": hey!";
                            }
                        });

                        String s = fut.get();

                        logger.debug("{}: {}", finalI, s);
                    } catch (Exception e) {
                        throw Exceptions.runtime(e);
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(entries.get()).isEqualTo(1);
    }
}
