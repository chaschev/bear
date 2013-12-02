package bear.plugins.java;

import bear.console.AbstractConsoleCommand;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.plugins.sh.MockedRemoteSession;
import bear.session.DynamicVariable;
import bear.session.SshAddress;
import bear.task.SessionTaskRunner;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static chaschev.lang.OpenBean.setField;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SessionTest {
    protected GlobalContext g;
    protected SessionContext $;

    protected MockedRemoteSession sys;
    protected List<AbstractConsoleCommand> commands;

    public SessionTest() {
        g = GlobalContext.newForTests();

        g.put("bear.sshUsername", "user");
        g.put("bear.sshPassword", "pass");

        SessionTaskRunner runner = new SessionTaskRunner(null, g);
        $ = new SessionContext(g, new SshAddress("u", "p", "host"), runner);
        runner.set$($);

        sys = spy(new MockedRemoteSession(g.getPlugin(GenericUnixRemoteEnvironmentPlugin.class), $.sys.getParent(), $));

        commands = new ArrayList<AbstractConsoleCommand>();

        when(sys.upload(anyString(), any(File[].class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock inv) throws Throwable {
                commands.add(sys.line().addRaw("<upload to " + inv.getArguments()[0] + ">"));

                return null;
            }
        });

        setField($, "sys", sys);

    }

    protected String $(DynamicVariable<String> x) {
        return $.var(x);
    }
}
