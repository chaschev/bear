package examples.java

import bear.context.Fun
import bear.core.*
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DbDumpManager
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.java.JavaPlugin
import bear.plugins.maven.MavenPlugin
import bear.plugins.mongo.MongoDbPlugin
import bear.plugins.mysql.MySqlPlugin
import bear.plugins.java.PlayPlugin
import bear.session.DynamicVariable
import bear.strategy.DeploymentPlugin
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.task.TaskResult
import bear.vcs.GitCLIPlugin

import java.util.regex.Pattern

import static bear.plugins.db.DumpManagerPlugin.DbType.mysql
import static bear.plugins.sh.WriteStringInput.str
import static bear.session.BearVariables.joinPath
import static bear.session.Variables.dynamic
import static bear.session.Variables.newVar
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
    MongoDbPlugin mongoPlugin;
    MySqlPlugin mysqlPlugin;
    DeploymentPlugin deployment;
    DumpManagerPlugin dumpManager;

    public final DynamicVariable<String> useDb = newVar(mysql.toString()),
//                                         serviceString = condition(isEql(useDb, "mysql"),
//                                             newVar("9998:service.SqlUserService"),
//                                             newVar("9998:service.MongoUserService"))
        serviceString = dynamic({_ -> _.var(useDb) == 'mysql' ? '9998:service.SqlUserService' : '9998:service.MongoUserService'} as Fun)
        ;
    ;

    public TaskDef<Task> deployProject;

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

        bear.stages.defaultTo(new Stages(global)
            .addSimple("one", "vm01")
            .addSimple("two", "vm01, vm02")
            .addSimple("three", "vm01, vm02, vm03"));

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
        (useDb): mysql.toString(),
        'd': 's'
    ];

    static main(def args)
    {
        def demo = new SecureSocialDemoProject()

        demo
            .set(demo.main().propertiesFile, new File(".bear/test.properties"))
            .configure()
            .dumpSampleGrid
            .withVars(demo.mysqlVars)
            .run();
    }

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

        return _.sys.writeString(str(pluginsPath, plugins));
    }
}
