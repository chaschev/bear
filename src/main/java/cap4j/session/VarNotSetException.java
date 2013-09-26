package cap4j.session;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class VarNotSetException extends RuntimeException {
    DynamicVariable variable;

    public VarNotSetException(DynamicVariable variable) {
        super("variable :" + variable.name + " was not set");
        this.variable = variable;
    }
}
