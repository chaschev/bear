package bear.plugins.sh;

import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.core.SessionContext;
import bear.session.Result;
import bear.task.Task;
import bear.vcs.CommandLineResult;

import java.io.File;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class MockedRemoteSession extends RemoteSystemSession{
    @Override
    public Result upload(String dest, File... files) {
        return Result.OK;
    }

    public MockedRemoteSession(GenericUnixRemoteEnvironmentPlugin remotePlugin, Task parent, SessionContext $) {
        super(remotePlugin, parent, $);
    }

    public <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command, ConsoleCallback callback) {
        return null;
    }

    @Override
    public Result writeStringAs(WriteStringInput input) {
        return null;
    }
}
