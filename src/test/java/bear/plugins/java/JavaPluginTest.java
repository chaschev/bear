package bear.plugins.java;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import bear.session.SshAddress;
import bear.task.SessionTaskRunner;
import org.junit.Test;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class JavaPluginTest {
    protected GlobalContext g;
    protected SessionContext sessionContext;
    protected final JavaPlugin java;

    public JavaPluginTest() {
        g = GlobalContext.newForTests();

        g.put("bear.sshUsername", "f");
        g.put("bear.sshPassword", "c");

        sessionContext = new SessionContext(g, new SshAddress("x", "y", "xx"), new SessionTaskRunner(null, g));
        java = new JavaPlugin(g);
    }

    @Test
    public void testVars() throws Exception {
        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        assertThat($(java.localDistrPath)).isEqualTo("/var/lib/bear/shared/tools/jdk/jdk-7u40-linux-x64.gz");
        assertThat($(java.distrFilename)).isEqualTo("jdk-7u40-linux-x64.gz");
        assertThat($(java.homePath)).isEqualTo("/var/lib/bear/tools/jdk/1.7.0_40");
        assertThat($(java.versionName)).isEqualTo("jdk-7u40-linux-x64");
        assertThat($(java.toolDistrName)).isEqualTo("jdk");
    }

    private String $(DynamicVariable<String> x) {
        return sessionContext.var(x);
    }
}
