package examples.node
import bear.core.*
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DbDumpManager
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.misc.ReleasesPlugin
import bear.plugins.mongo.MongoDbPlugin
import bear.plugins.nodejs.NodeJsPlugin
import bear.strategy.DeploymentPlugin
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.vcs.GitCLIPlugin

import static bear.plugins.db.DumpManagerPlugin.DbType.mongo
import static bear.plugins.sh.CaptureInput.cap
import static bear.plugins.sh.CopyOperationInput.mv
import static bear.task.TaskResult.OK
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

public class DrywallDemoProject extends BearProject<DrywallDemoProject> {
    // these are the plugins which are injected
    Bear bear;
    GitCLIPlugin git;
    NodeJsPlugin nodeJs;
    MongoDbPlugin mongoPlugin;
    DeploymentPlugin deployment;
    ReleasesPlugin releases;
    DumpManagerPlugin dumpManager;

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

    TaskDef<Task> runGrunt = new TaskDef<Task>({ SessionContext _, Task<TaskDef> task, Object input ->
        final String dir = _.var(releases.activatedRelease).get().path

        _.sys.move(mv("config.example.js", "config.js").cd(dir)).throwIfError();
        _.sys.captureResult(cap("grunt").cd(dir)).throwIfError()
    }as TaskCallable<TaskDef>);

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        nodeJs.version.set("0.10.22");

        bear.vcsBranchName.defaultTo("master");

        dumpManager.dbType.set(mongo.toString());

        nodeJs.projectPath.setEqualTo(bear.vcsBranchLocalPath);

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
            .BuildAndCopy_3({ _, task, input -> _.run(nodeJs.build, runGrunt); } as TaskCallable)
            .StopService_5({ _, task, input -> _.run(nodeJs.stop); OK; } as TaskCallable)
            .StartService_8({ _, task, input -> _.run(nodeJs.start, nodeJs.watchStart); } as TaskCallable)
            .endDeploy()
            .ifRollback()
              .beforeLinkSwitch({ _, task, input -> _.run(nodeJs.stop); } as TaskCallable)
              .afterLinkSwitch({ _, task, input -> _.run(nodeJs.start, nodeJs.watchStart); } as TaskCallable)
            .endRollback()
            .build();

        return global;
    }

    @Override
    GridBuilder defaultGrid()
    {
        return dumpSampleGrid;
    }

    static main(def args)
    {
        def demo = new DrywallDemoProject()

        deploy(demo)
    }

    private static void deploy(DrywallDemoProject demo)
    {
        demo
            .set(demo.main().propertiesFile, new File(".bear/drywall.properties"))
            .configure()
            .newGrid()
            .add(demo.deployProject)
            .run()
    }

    private static void setup(DrywallDemoProject demo)
    {
        demo
            .set(demo.main().propertiesFile, new File(".bear/drywall.properties"))
            .configure()
            .set(demo.bear.verifyPlugins, true)
            .set(demo.bear.autoInstallPlugins, true)
            .set(demo.bear.checkDependencies, true)
            .newGrid()
            .add(demo.global.tasks.setup)
            .run()
    }
}
