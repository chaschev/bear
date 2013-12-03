package bear.vcs;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CommandLineOperator {
    String s;

    public CommandLineOperator(String s) {
        this.s = s;
    }

    @Override
    public String toString() {
        return s;
    }
}
