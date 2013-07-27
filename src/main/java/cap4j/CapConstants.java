package cap4j;

import cap4j.session.Variable;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class CapConstants {
    public static final String[] devEnvironments = {"dev", "test", "prod"};

    public static final Variable<String> devEnvironment = new Variable<String>(new Name("devEnv"), "Development environment") {
        @Override
        public void validate(String value) {
            if(!ArrayUtils.contains(devEnvironments, value)){
                Preconditions.checkArgument(false, "devEnv must be one of: " + Arrays.asList(devEnvironments));
            }
        }
    };
}
