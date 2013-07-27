package cap4j;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class Name implements Nameable {
    String name;

    public Name(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }
}
