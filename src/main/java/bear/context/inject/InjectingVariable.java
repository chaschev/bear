package bear.context.inject;

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.session.DynamicVariable;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/

//todo add builder, move scopes to the builder
public class InjectingVariable<T> extends DynamicVariable<T> {
    protected InjectingScope[] restrictedDeclaringClasses;
    protected InjectingScope[] restrictedTypes;

    {
        name = "InjectingVariable";
    }

    //restrict declared classes to, OR logic
    public InjectingScope[] getDeclaredClassScope(){
        return restrictedDeclaringClasses;
    }
    //restrict field types to, OR logic
    public InjectingScope[] getType(){
        return restrictedTypes;
    }

    public InjectingVariable<T> restrictDeclaringClasses(InjectingScope... restrictedDeclaringClasses){
        this.restrictedDeclaringClasses = restrictedDeclaringClasses;
        return this;
    }

    public InjectingVariable<T> restrictDeclaringClasses(Class... restrictedDeclaringClasses){
        return restrictDeclaringClasses(convertToScopes(restrictedDeclaringClasses));
    }

    public InjectingVariable<T> restrictTypes(InjectingScope... restrictedTypes){
        this.restrictedTypes = restrictedTypes;
        return this;
    }

    public InjectingVariable<T> restrictTypes(Class... restrictedTypes){
        return restrictTypes(convertToScopes(restrictedTypes));
    }

    private static InjectingScope[] convertToScopes(Class[] restrictedDeclaringClasses) {
        InjectingScope[] ar = new InjectingScope[restrictedDeclaringClasses.length];

        for (int i = 0; i < restrictedDeclaringClasses.length; i++) {
            ar[i] = new InjectingScope(true, restrictedDeclaringClasses[i]);
        }
        return ar;
    }

    @Override
    public DynamicVariable<T> set(T defaultValue) {
        super.set(defaultValue);
        return this;
    }

    @Override
    public InjectingVariable<T> setDynamic(Fun<? extends AbstractContext, T> impl) {
        super.setDynamic(impl);
        return this;
    }
}
