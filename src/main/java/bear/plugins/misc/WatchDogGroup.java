package bear.plugins.misc;

import bear.session.DynamicVariable;
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
    final DynamicVariable<WatchDogGroup> watchDogGroup;

    public WatchDogGroup(int count, DynamicVariable<WatchDogGroup> watchDogGroup) {
        this.watchDogGroup = watchDogGroup;
        this.runnables = new ArrayList<WatchDogRunnable>(count);
        this.arrivalLatch = new CountDownLatch(count);
    }

    public void shutdownNow() {
        runnables.get(0).$.removeConst(watchDogGroup);

        for (WatchDogRunnable runnable : runnables) {
            if (!runnable.finished) {
                runnable.thread.interrupt();
            }
        }
    }

    public void add(WatchDogRunnable runnable) {
        runnables.add(runnable);
        runnable.arrivalLatch = arrivalLatch;
        runnable.group = this;
    }

    public void startThreads() {
        runnables.get(0).$.putConst(watchDogGroup, this);

        for (WatchDogRunnable runnable : runnables) {
            (runnable.thread = new Thread(new CatchyRunnable(runnable))).start();
        }
    }

    public CountDownLatch latch() {
        return arrivalLatch;
    }
}
