package bear.main.phaser;

import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class OnceEnteredCallable<T> {
    final ReentrantLock lock = new ReentrantLock();
    Thread owner;
    final SettableFuture<T> future = new SettableFuture<T>(){
        @Override
        protected void interruptTask() {
            if(owner != null){
                owner.interrupt();
            }
        }
    };

    volatile boolean called;

    public OnceEnteredCallable() {
    }

    public ListenableFuture<T> runOnce(Callable<T> callable) {
        if(lock.tryLock()){
            owner = Thread.currentThread();

            if(called){
                return future;
            }

            try {
                T r = callable.call();
                future.set(r);
            } catch (Exception e) {
                future.setException(e);
                LoggerFactory.getLogger("log").warn("", e);
            } finally {
                called = true;
                lock.unlock();
            }
        }

        return future;
    }

    public boolean amIOwner(Thread thread) {
        return owner == thread;
    }
}
