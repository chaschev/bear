package bear.main.phaser;

import chaschev.lang.Lists2;
import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Phase<V> {
    String name;
    int rowIndex;
    final Function<Integer, PhaseCallable<?, V>> factory;
    final List<? extends PhaseCallable<?, V>> parties = new ArrayList();

    public Phase(List<? extends PhaseCallable<?, V>> parties) {
        this.parties.addAll((List)parties);
        factory = null;
    }

    public <C> Phase(String name, Function<Integer, PhaseCallable<C, V>> factory) {
        this.name = name;
        this.factory = (Function) factory;
    }

    public <C> Phase(Function<Integer, PhaseCallable<C, V>> factory) {
        this(null, factory);
    }

    public <C> List<? extends PhaseCallable<C, V>> getParties(ComputingGrid<C> grid) {
        if(factory == null) return (List) parties;

        if(parties.isEmpty()){
            parties.addAll((List) Lists2.computingList(grid.parties.size(), factory));
        }

        return (List) parties;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[Phase ");
        sb.append("'").append(name).append('\'');
        sb.append(", phaseIndex=").append(rowIndex);
        sb.append(']');
        return sb.toString();
    }
}
