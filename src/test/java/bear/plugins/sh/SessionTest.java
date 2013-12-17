package bear.plugins.sh;

import bear.console.AbstractConsoleCommand;
import bear.context.HavingContext;
import bear.core.GlobalContext;
import bear.core.GlobalContextTest;
import bear.core.SessionContext;
import bear.session.Result;
import bear.session.SshAddress;
import bear.session.Versions;
import bear.task.SessionRunner;
import bear.vcs.CommandLineResult;
import chaschev.util.Exceptions;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static chaschev.lang.OpenBean.setField;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SessionTest extends HavingContext<SessionTest, SessionContext>{
    private static final SystemSession.OSInfo osInfo;

    static {
        try {
            osInfo = new SystemSession.OSInfo(UnixFlavour.CENTOS, UnixSubFlavour.CENTOS, Versions.VERSION_SCHEME.parseVersion("6.4"));
        } catch (InvalidVersionSpecificationException e) {
            throw Exceptions.runtime(e);
        }
    }

    protected GlobalContext g;

    protected SystemSession sys;
    protected List<Object> commands;

    public SessionTest() {
        super(null);
        g = GlobalContextTest.newGlobal();

        g.put(g.bear.sshUsername, "user");
        g.put(g.bear.sshPassword, "pass");
        g.put(g.bear.name, "unitTests");

        SessionRunner runner = new SessionRunner(null, g);
        $ = new SessionContext(g, new SshAddress("u", "p", "host"), runner);

        runner.set$($);

        sys = spy($.sys);

        commands = new ArrayList<Object>();

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock inv) throws Throwable {
                commands.add("<upload to " + inv.getArguments()[0] + ">");

                return null;
            }
        }).when(sys).upload(anyString(), any(File[].class));
//        when(sys.upload(anyString(), any(File[].class))).thenAnswer();

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock inv) throws Throwable {
                commands.add(inv.getArguments()[0]);
                return Result.OK;
            }
        }).when(sys).writeString(any(WriteStringInput.class));

//        stubSendCommand();

        doReturn(centos()).when(sys).computeUnixFlavour();
//        when(sys.writeStringAs(any(WriteStringInput.class))).thenAnswer();

        setField($, "sys", sys);
    }

    protected void stubSendCommand() {
        doReturn(CommandLineResult.OK).when(sys).sendCommand(any(AbstractConsoleCommand.class));
    }

    private static SystemSession.OSInfo centos()  {
            return osInfo;

    }
}
