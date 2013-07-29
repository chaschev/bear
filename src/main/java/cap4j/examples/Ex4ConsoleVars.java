package cap4j.examples;

import cap4j.Console;
import cap4j.GlobalContext;
import cap4j.Nameable;
import cap4j.Stage;
import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;

import static cap4j.CapConstants.devEnvironment;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class Ex4ConsoleVars {
    static enum ProjectVars implements Nameable{
        quickDeploy
    }

    public static void main(String[] args) {
        final Stage pacDev = new Stage("pac-dev")
            .add(newUnixRemote("chaschev", "aaaaaa", "192.168.25.66"))
            ;

        final Task<TaskResult> testTask = new Task<TaskResult>() {
            @Override
            protected void defineVars(Console console) {
                console.askIfUnset("Deploy quickly?", ProjectVars.quickDeploy, !"prod".equals(GlobalContext.var(devEnvironment)));
            }

            @Override
            protected TaskResult run(TaskRunner runner) {
                system.copy("src", "dest");
                system.runForEnvironment("linux", new SystemEnvironments.EnvRunnable() {
                    @Override
                    public Result run(SystemEnvironment system) {
                        return system.run("echo blahblahblah").result;
                    }
                });

                return new TaskResult(Result.OK);
            }
        };

        pacDev.runTask(testTask);
    }
}
