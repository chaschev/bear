package bear.plugins;

import bear.core.GlobalContext;
import bear.session.DynamicVariable;
import org.junit.Test;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ZippedToolPluginTest {

    private GlobalContext g = GlobalContext.newForTests();

    @Test
    public void testVars() throws Exception {

        ZippedToolPlugin zip = new ZippedToolPlugin(g);

        zip.version.set("2.0");
        zip.toolname.set("my-tool");

        assertThat($(zip.versionName)).isEqualTo("my-tool-2.0");
        assertThat($(zip.distrFilename)).isEqualTo("my-tool-2.0.tar.gz");
        assertThat($(zip.homeParentPath)).isEqualTo("/var/lib/bear/tools/my-tool");
        assertThat($(zip.homePath)).isEqualTo("/var/lib/bear/tools/my-tool/2.0");
//        assertThat($(zip.homeVersionPath)).isEqualTo("/var/lib/bear/tools/my-tool/2.0");
    }

    private String $(DynamicVariable<String> x) {
        return g.var(x);
    }
}
