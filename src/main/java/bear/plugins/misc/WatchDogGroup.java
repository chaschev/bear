package bear.plugins.misc;

import chaschev.util.CatchyRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WatchDogGroup {
    final List<WatchDogRunnable> runnables;
    private final CountDownLatch arrivalLatch;

    public WatchDogGroup(int count) {
        this.runnables = new ArrayList<WatchDogRunnable>(count);
        this.arrivalLatch = new CountDownLatch(count);
    }

    public void shutdownNow() {
        for (WatchDogRunnable runnable : runnables) {
            if (!runnable.finished) {
                runnable.thread.interrupt();
            }
        }
    }

    public void add(WatchDogRunnable runnable) {
        runnables.add(runnable);
        runnable.arrivalLatch = arrivalLatch;
    }

    public void startThreads() {
        for (WatchDogRunnable runnable : runnables) {
            (runnable.thread = new Thread(new CatchyRunnable(runnable))).start();
        }
    }

    public CountDownLatch latch() {
        return arrivalLatch;
    }
}
