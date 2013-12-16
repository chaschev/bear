package bear.plugins.misc;

import com.google.common.base.Optional;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.of;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class UpstartService {
    String name;
    String description;
    String script;
    Optional<String> custom = absent();
    Optional<String> user = absent();
    Optional<String> group = absent();

    Optional<String> dir = absent();

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

    public UpstartService setUser(String user) {
        this.user = of(user);
        return this;
    }

    public UpstartService setGroup(String group) {
        this.group = of(group);
        return this;
    }

    public void setCustom(String custom) {
        this.custom = of(custom);
    }

    public UpstartService exportVar(String name, String value){
        exportVars.put(name, value);
        return this;
    }

    public String dir(){
        return dir.get();
    }


}
