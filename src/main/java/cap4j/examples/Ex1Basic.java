package cap4j.examples;

import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import cap4j.task.Task;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class Ex1Basic {
    public static void main(String[] args) {
        new Task(){
            {

            }

            @Override
            protected TaskResult run() {
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
    }
}
