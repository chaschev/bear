package examples

import bear.core.*
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DbDumpManager
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.java.JavaPlugin
import bear.plugins.maven.MavenPlugin
import bear.plugins.mongo.MongoDbPlugin
import bear.plugins.mysql.MySqlPlugin
import bear.plugins.play.PlayPlugin
import bear.session.DynamicVariable
import bear.strategy.DeploymentPlugin
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.task.TaskResult
import bear.vcs.GitCLIPlugin

import java.util.regex.Pattern

import static bear.session.BearVariables.joinPath
import static bear.session.Variables.*
import static bear.task.TaskResult.OK
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

public class SecureSocialDemoProject extends BearProject<SecureSocialDemoProject> {
    // these are the plugins which are injected
    JavaPlugin java;
    MavenPlugin maven;
    Bear bear;
    GitCLIPlugin git;
    PlayPlugin play;
    MongoDbPlugin mongo;
    MySqlPlugin mysql;
    DeploymentPlugin deployment;
    DumpManagerPlugin dumpManager;

    public final DynamicVariable<String> useDb = newVar("mysql"),
                                         serviceString = condition(isEql(useDb, "mysql"),
                                             newVar("9998:service.SqlUserService"),
                                             newVar("9998:service.MongoUserService"));

    public TaskDef<Task> deployProject;

    GridBuilder dumpSampleGrid = new GridBuilder()
        .add({ SessionContext _, Task task, Object input ->

        final DbDumpManager.DbService dumpService = _.var(dumpManager.dbService)

        final DbDumpInfo dump = dumpService.createDump("ss_demo")

        final List<DbDumpInfo> dumps = dumpService.listDumps()

        println "created dump $dump, rolling back to ${dumps[0]}"

        dumpService.restoreDump(dumps[0])

        println dumpService.printDumpInfo(dumpService.listDumps())

        return OK
    } as TaskCallable<TaskDef>);

    private TaskResult updateDbConf(SessionContext _)
    {
        String pluginsPath = _.var(play.projectPath) + "/conf/play.plugins";

        String plugins = _.sys.readString(pluginsPath, null);

        plugins = DB_LINE_PATTERN
            .matcher(plugins)
            .replaceFirst(_.var(serviceString));

        return _.sys.writeString(pluginsPath, plugins).toTaskResult();
    }

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        maven.version.set("3.0.5");

        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        play.version.set("2.2.0");

        bear.vcsBranchName.defaultTo("master");

        play.projectPath.setEqualTo(joinPath(bear.vcsBranchLocalPath, "samples/java/db-demo"));

        dumpManager.dbType.setEqualTo(useDb);

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

        // this defines the deployment task
        deployProject = deployment.newBuilder()
            .CheckoutFiles_2({ _, task, input -> _.run(global.tasks.vcsUpdate); } as TaskCallable)
            .BuildAndCopy_3({ _, task, input -> updateDbConf(_); _.run(play.build); } as TaskCallable)
            .StopService_5({ _, task, input -> _.run(play.stop); OK; } as TaskCallable)
            .StartService_8({ _, task, input -> _.run(play.start, play.watchStart); } as TaskCallable)
            .endDeploy()
            .ifRollback()
              .beforeLinkSwitch({ _, task, input -> _.run(play.stop); } as TaskCallable)
              .afterLinkSwitch({ _, task, input -> _.run(play.start, play.watchStart); } as TaskCallable)
            .endRollback()
            .build();

        return global;
    }


    private static final Pattern DB_LINE_PATTERN = Pattern.compile("9998:[^\\n]+", Pattern.DOTALL | Pattern.MULTILINE);

    @Override
    GridBuilder defaultGrid()
    {
        return dumpSampleGrid;
    }

    def mysqlVars = [
        (useDb): "mysql",
        'd': 's'
    ];

    static main(def args)
    {

        //same as
        //BearMain -VsecureSocialDemoProject.useDb=mysql -VbearMain.project=SecureSocialDemoProject
        def demo = new SecureSocialDemoProject()
        def file = demo.main().propertiesFile

        demo
            .set(file, new File(".bear/test.properties"))

        demo.configure()
            .dumpSampleGrid
            .withVars(demo.mysqlVars)
            .run();

    }


}
