package bear.plugins.misc;

import bear.context.DependencyInjection;
import bear.plugins.sh.SessionTest;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

//todo need to fix mocking for sessions
@Ignore
public class UpstartPluginTest extends SessionTest{
    private final UpstartPlugin upstart;

    public UpstartPluginTest() {
        upstart = new UpstartPlugin(g);
        DependencyInjection.nameVars(upstart, g);
    }

    @Test
    public void testUpstart() throws Exception {
        $.runner.runSession(upstart.create.singleTaskSupplier().createNewSession($, sys, upstart.create),
            new UpstartServices(
                Optional.of("my_scripts"),
                Lists.newArrayList(
                    new UpstartService("ss_9000", "Secure Social Demo instance on port 9000", "blahblahblah"),
                    new UpstartService("ss_9001", "Secure Social Demo instance on port 9001", "blahblahblah"),
                    new UpstartService("ss_9002", "Secure Social Demo instance on port 9002", "blahblahblah")
                )
            )).throwIfError();

        System.out.println("commands: \n" + Joiner.on("\n").join(commands));
    }
}
