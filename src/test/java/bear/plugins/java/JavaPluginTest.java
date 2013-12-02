package bear.plugins.java;

import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.vcs.CommandLineResult;
import com.google.common.base.Joiner;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class JavaPluginTest extends SessionTest {
    protected final JavaPlugin java;

    public JavaPluginTest() {
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

        when(sys.sendCommand(any(AbstractConsoleCommand.class), any(ConsoleCallback.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                AbstractConsoleCommand command = (AbstractConsoleCommand) invocationOnMock.getArguments()[0];

                commands.add(command);

                String asString = command.toString();

                if (asString.contains("ls -w 1")) {
                    return new CommandLineResult("jdk1.7.0_40");
                }

                return new CommandLineResult("foo");
            }
        });


        $.run(java.getInstall());

        System.out.println("commands: \n" + Joiner.on("\n").join(commands));

    }

}
