package bear.plugins;

import com.google.common.collect.Iterables;

import java.util.HashSet;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class PluginShellMode {
    protected String commandName;
    protected String description;

    public static class SshShellMode{
        public static void main(String[] args) {
            for (List<String> partition : Iterables.partition(new HashSet<String>(), 100)) {
                // ... handle partition ...
            }

        }
    }


}
