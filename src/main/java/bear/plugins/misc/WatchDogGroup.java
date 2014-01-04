package bear.plugins.misc;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import bear.task.TaskResult;
import bear.task.Tasks;
import chaschev.util.CatchyRunnable;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WatchDogGroup {
    final List<WatchDogRunnable> runnables;
    private final CountDownLatch arrivalLatch;
    final DynamicVariable<WatchDogGroup> watchDogGroup;
    private volatile ListenableScheduledFuture<?> forcedShutdownFuture;
    SessionContext $;

    public WatchDogGroup(int count, DynamicVariable<WatchDogGroup> watchDogGroup) {
        this.watchDogGroup = watchDogGroup;
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
        if($== null){
            $ = runnable.$;
        }


        runnables.add(runnable);
        runnable.arrivalLatch = arrivalLatch;
        runnable.group = this;
    }

    public void startThreads() {
        $.putConst(watchDogGroup, this);

        int i = 1;
        String myName = Thread.currentThread().getName();
        for (WatchDogRunnable runnable : runnables) {
            (runnable.thread = new GlobalContext.AwareThread(new CatchyRunnable(runnable))).start();
            runnable.thread.setName(myName + "-dog-" + i);
            i++;
        }
    }

    public CountDownLatch latch() {
        return arrivalLatch;
    }

    public void scheduleForcedShutdown(ListeningScheduledExecutorService scheduler, int timeout, TimeUnit unit) {
        forcedShutdownFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if(arrivalLatch.getCount() > 0){
                    WatchDogGroup.this.shutdownNow();
                }
            }
        }, timeout, unit);
    }

    public void whenArrived(WatchDogRunnable watchDogRunnable) {
        arrivalLatch.countDown();

        if(arrivalLatch.getCount() == 0){
            forcedShutdownFuture.cancel(true);
        }
    }

    public TaskResult<?> getResult(){
        List<TaskResult<?>> temp = new ArrayList<TaskResult<?>>(runnables.size());

        for (WatchDogRunnable runnable : runnables) {
            temp.add(runnable.getResult() == null ? TaskResult.error("null result for watch dog") : runnable.getResult());
        }

        return Tasks.and(temp);
    }
}
