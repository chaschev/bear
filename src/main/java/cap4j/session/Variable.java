package cap4j.session;

import cap4j.Nameable;
import cap4j.VariableName;
import cap4j.Variables;
import com.google.common.base.Function;

import javax.annotation.Nullable;

/**
* User: chaschev
* Date: 7/21/13
*/
public class Variable<T> implements Function<Variables.Context, T>, Nameable {
    public boolean frozen;

    public final String name;
    public final String title;

    public Variable(String title) {
        this.name = "-";
        this.title = title;
    }

    public Variable(Nameable varName, String title) {
        this.name = varName.name();
        this.title = title;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void validate(T value){
        //todo embed
        return true;
    }

    @Override
    public String name() {
        return name;
    }

    @Nullable
    @Override
    public T apply(Variables.Context context) {
        return context.globalContext.variables.get(this, null);
    }
}
