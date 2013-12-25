package bear.main.phaser;

import javax.annotation.Nullable;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public final class GridCell<COL, V, PHASE> {
    public long startedAtMs;
    public long finishedAtMs;

    private SettableFuture<V> future;

    @Nullable
    PhaseCallable<COL, V, PHASE> callable;

    @Nullable
    WhenDone<COL, V,PHASE> whenDone;

    public synchronized SettableFuture<V> getFuture() {
        if(future == null){
            future = new SettableFuture<V>();
        }
        return future;
    }
    public GridCell started() {
        startedAtMs = System.currentTimeMillis();
        return this;
    }

}
