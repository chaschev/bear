import bear.core.*;
import bear.plugins.java.JavaPlugin;
import bear.plugins.maven.MavenPlugin;
import bear.plugins.mongo.MongoDbPlugin;
import bear.plugins.mysql.MySqlPlugin;
import bear.plugins.play.PlayPlugin;
import bear.strategy.DeploymentBuilder;
import bear.task.Task;
import bear.task.TaskCallable;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.vcs.GitCLIPlugin;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SecureSocialDemoSettings extends IBearSettings {
    JavaPlugin java;
    MavenPlugin maven;
    Bear bear;
    GlobalContext global;
    GitCLIPlugin git;
    PlayPlugin play;
    MongoDbPlugin mongo;
    MySqlPlugin mysql;

    public SecureSocialDemoSettings(GlobalContextFactory factory) {
        super(factory);
    }

    public SecureSocialDemoSettings(GlobalContextFactory factory, String resource) {
        super(factory, resource);
    }

    public final TaskDef<Task> deployProject = new DeploymentBuilder()
        .CheckoutFiles_2(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                return $.run(global.tasks.vcsUpdate);
            }
        })
        .BuildAndCopy_3(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                return $.run(play.stage);
            }
        })
        .StopService_5(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                return $.run(play.stageStop);
            }
        })
        .StartService_8(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                return $.run(play.stageStart);
            }
        })
        .done()
        .build();

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
        factory.init(this);

        maven.version.set("3.0.5");

//        mongo.version.set("LATEST");

        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        play.version.set("2.2.1");

        bear.vcsBranchName.defaultTo("master");

        Stages stages = new Stages(global);

        bear.stages.defaultTo(
            stages
                .add(
                    new Stage("one")
                        .addHosts(stages.hosts("vm01")))
                .add(
                    new Stage("two")
                        .addHosts(stages.hosts("vm01, vm02")))
                .add(
                    new Stage("three")
                        .addHosts(stages.hosts("vm01, vm02, vm03"))
                ));

        return global;
    }
}
