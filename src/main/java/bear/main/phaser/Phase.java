package bear.main.phaser;

import chaschev.lang.Lists2;
import chaschev.lang.reflect.MethodDesc;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static chaschev.lang.OpenBean.getMethod;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Phase<V, PHASE> {
    String name;
    int rowIndex;
    final Function<Integer, PhaseCallable<?, V, PHASE>> factory;
    final List<? extends PhaseCallable<?, V, PHASE>> parties = new ArrayList();

    PHASE phase;

    ComputingGrid<?, PHASE> grid;

    OnceEnteredCallable<?> once;

    public Phase(List<? extends PhaseCallable<?, V, PHASE>> parties) {
        this.parties.addAll((List)parties);
        factory = null;
    }

    public <C> Phase(PHASE phase, Function<Integer, PhaseCallable<C, V, PHASE>> factory) {
        Preconditions.checkNotNull(phase);
        this.phase = phase;
        this.factory = (Function) factory;

        Optional<MethodDesc> method = getMethod(phase, "getName").or(getMethod(phase, "name"));

        if(method.isPresent()){
            this.name = (String) method.get().invoke(phase);
        }
    }

    public <C> List<? extends PhaseCallable<C, V, PHASE>> getParties(ComputingGrid<C, PHASE> grid) {
        if(factory == null) return (List) parties;

        if(parties.isEmpty()){
            parties.addAll((List) Lists2.computingList(grid.parties.size(), factory));
        }

        return (List) parties;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[Phase ");
        sb.append("'").append(name).append('\'');
        sb.append(", phaseIndex=").append(rowIndex);
        sb.append(']');
        return sb.toString();
    }

    public PHASE getPhase() {
        return phase;
    }

    public String getName() {
        return name;
    }

    public synchronized <T> OnceEnteredCallable<T> getOnce(){
        if (once == null){
            once = new OnceEnteredCallable<Object>();
        }

        return (OnceEnteredCallable<T>) once;
    }

    public <T> ListenableFuture<T> callOnce(Callable<T> callable){
        return (ListenableFuture<T>) getOnce().runOnce((Callable)callable);
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public <T> Phase<T, PHASE> getRelativePhase(int offset, Class<T> tClass){
        return (Phase<T, PHASE>) grid.phases.get(rowIndex + offset);
    }
}
