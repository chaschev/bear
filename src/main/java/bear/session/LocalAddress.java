package bear.session;

import bear.core.Bear;
import bear.core.WireFields;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@WireFields(Bear.class)
public class LocalAddress extends Address {
    public LocalAddress() {
        super("localhost");
    }

    @Override
    public String getAddress() {
        return "localhost";
    }
}
