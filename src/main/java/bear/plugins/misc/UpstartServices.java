package bear.plugins.misc;

import com.google.common.base.Optional;

import javax.annotation.Nonnull;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class UpstartServices {
    /**
     * Will be placed to /usr/bin/groupName_[start|stop|status|restart] when specified.
     */
    final Optional<String> groupName;

    final List<UpstartService> services;

    public UpstartServices(@Nonnull Optional<String> groupName, @Nonnull List<UpstartService> services) {
        this.groupName = groupName;
        this.services = services;
    }


}
