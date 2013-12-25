package bear.main.phaser;

import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractFuture;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class SettableFuture<V> extends AbstractFuture<V> {
    @Override
    public boolean set(@Nullable V value) {
         return super.set(value);
    }

    @Override
    public boolean setException(Throwable throwable) {
        return super.setException(throwable);
    }

//    private static final Field syncField;
//    private static final Field exceptionField;
//
//    static {
//        try {
//            syncField = OpenBean.getClassDesc(AbstractFuture.class).getField("sync");
//            exceptionField = OpenBean.getClassDesc(Class.forName(AbstractFuture.class.getName() + "$Sync")).getField("exception");
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//            throw Exceptions.runtime(e);
//        }
//    }

    public Throwable getException(){
        Preconditions.checkState(isDone(), "future is not finished");

        try {
            V v = get();
        } catch (InterruptedException e) {
            throw Exceptions.runtime(e);
        } catch (ExecutionException e) {
            return e.getCause();
        }

        return null;
    }
}
