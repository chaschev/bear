package bear.main.phaser;

import com.google.common.util.concurrent.AbstractFuture;

import javax.annotation.Nullable;

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
}
