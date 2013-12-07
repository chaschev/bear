import bear.core.*;
import bear.plugins.java.JavaPlugin;
import bear.plugins.maven.MavenPlugin;
import bear.plugins.mongo.MongoDbPlugin;
import bear.plugins.mysql.MySqlPlugin;
import bear.plugins.play.PlayPlugin;
import bear.session.DynamicVariable;
import bear.strategy.DeploymentBuilder;
import bear.task.Task;
import bear.task.TaskCallable;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.vcs.GitCLIPlugin;

import java.util.regex.Pattern;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.*;
import static bear.task.TaskResult.OK;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SecureSocialDemoSettings extends IBearSettings {
    // these are the plugins which are injected
    JavaPlugin java;
    MavenPlugin maven;
    Bear bear;
    GlobalContext global;
    GitCLIPlugin git;
    PlayPlugin play;
    MongoDbPlugin mongo;
    MySqlPlugin mysql;

    public final DynamicVariable<String>
        useDb = newVar("mysql"),
        serviceString = condition(isEql(useDb, "mysql"),
                newVar("9998:service.SqlUserService"),
                newVar("9998:service.MongoUserService"));

    public SecureSocialDemoSettings(GlobalContextFactory factory) {
        super(factory);
    }

    // this defines the deployment
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
                 updateDbConf($); return $.run(play.build);
            }
        })
        .StopService_5(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                $.run(play.stop); return OK;
            }
        })
        .StartService_8(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                return $.run(play.start);
            }
        })
        .WaitForServiceToStart_9(new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                return $.run(play.watchStart);
            }
        })
        .done()
        .build();

    private TaskResult updateDbConf(SessionContext $) {
        String pluginsPath = $.var(play.projectPath) + "/conf/play.plugins";

        String plugins = $.sys.readString(pluginsPath, null);

        plugins = DB_LINE_PATTERN
            .matcher(plugins)
            .replaceFirst($.var(serviceString));

        return $.sys.writeString(pluginsPath, plugins).toTaskResult();
    }

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
        factory.init(this);

        maven.version.set("3.0.5");

        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        play.version.set("2.2.0");

        bear.vcsBranchName.defaultTo("master");

        play.projectPath.setEqualTo(joinPath(bear.vcsBranchLocalPath, "samples/java/db-demo"));

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

    //9998:service.SqlUserService

    private static final Pattern DB_LINE_PATTERN = Pattern.compile("9998:[^\\n]+", Pattern.DOTALL | Pattern.MULTILINE);

}
