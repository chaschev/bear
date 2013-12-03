package bear.plugins.misc;

import bear.core.SessionContext;

import java.util.concurrent.CountDownLatch;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WatchDogRunnable implements Runnable {
    public Thread thread;
    WatchDogInput input;
    SessionContext $;
    FileWatchDogPlugin watchDog;
    volatile boolean finished;
    CountDownLatch arrivalLatch;

    public WatchDogRunnable(SessionContext $, FileWatchDogPlugin watchDog, WatchDogInput input) {
        this.$ = $;
        this.watchDog = watchDog;
        this.input = input;
    }

    @Override
    public void run() {
        try {
            watchDog.watch($, input);
        } finally {
            arrivalLatch.countDown();
            finished = true;
        }
    }
}
