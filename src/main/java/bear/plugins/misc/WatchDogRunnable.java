package bear.plugins.misc;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;

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
    WatchDogGroup group;
    private volatile CommandLineResult<?> result;

    public WatchDogRunnable(SessionContext $, FileWatchDogPlugin watchDog, WatchDogInput input) {
        this.$ = $;
        this.watchDog = watchDog;
        this.input = input;
    }

    @Override
    public void run() {
        try {
            result = watchDog.watch($, input);
        }

        finally {
            finished = true;

            if(group != null){
                group.whenArrived(this);
            }else{
                arrivalLatch.countDown();
            }
        }
    }

    public CommandLineResult<?> getResult() {
        return result;
    }
}
