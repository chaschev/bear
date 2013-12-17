package bear.plugins;

import bear.plugins.misc.UpstartService;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ConfigureServiceInput {
    public final String port;
    public final UpstartService service;

    public ConfigureServiceInput(String port, UpstartService service) {
        this.port = port;
        this.service = service;
    }
}
