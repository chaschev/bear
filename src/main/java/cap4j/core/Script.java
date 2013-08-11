package cap4j.core;

import java.io.File;

/**
 * User: achaschev
 * Date: 8/5/13
 * Time: 7:46 PM
 */
public abstract class Script {
    public File scriptsDir;

    public abstract void run() throws Exception;
}
