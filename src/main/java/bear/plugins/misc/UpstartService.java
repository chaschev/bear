package bear.plugins.misc;

import com.google.common.base.Optional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class UpstartService {
    String name;
    String description;
    String script;
    String custom;

    Optional<String> dir = Optional.absent();

    Map<String, String> exportVars = new LinkedHashMap<String, String>();

    public UpstartService(String name, String description, String script) {
        this.name = name;
        this.description = description;
        this.script = script;
    }

    public UpstartService cd(String dir) {
        this.dir = Optional.of(dir);
        return this;
    }
}
