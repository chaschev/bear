package examples.nodejs
import bear.context.Fun
import bear.core.*
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.misc.ReleasesPlugin
import bear.plugins.mongo.MongoDbPlugin
import bear.plugins.nodejs.NodeJsPlugin
import bear.plugins.ConfigureServiceInput
import bear.strategy.DeploymentPlugin
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.vcs.GitCLIPlugin
import com.google.common.base.Function

import static bear.plugins.db.DumpManagerPlugin.DbType.mongo
import static bear.plugins.sh.CopyOperationInput.cp
import static bear.task.TaskResult.OK
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

public class NodeExpressMongooseDemoProject extends BearProject<NodeExpressMongooseDemoProject> {
    // these are the plugins which are injected
    Bear bear;
    GitCLIPlugin git;
    NodeJsPlugin nodeJs;
    MongoDbPlugin mongoPlugin;
    DeploymentPlugin deployment;
    ReleasesPlugin releases;
    DumpManagerPlugin dumpManager;

    public TaskDef<Task> deployProject;

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        nodeJs.version.set("0.10.22");
        nodeJs.appCommand.set("server.js")
        nodeJs.projectPath.setEqualTo(bear.vcsBranchLocalPath);

        nodeJs.instancePorts.set("5000, 5001")

        bear.vcsBranchName.defaultTo("master");

        dumpManager.dbType.set(mongo.toString());

        nodeJs.configureService.setDynamic({ SessionContext _ ->
            return { ConfigureServiceInput input ->
                input.service
                    .cd(_.var(releases.activatedRelease).get().path)
                    .exportVar("PORT", input.port + "");

                return null;
            } as Function;
        } as Fun);

        bear.stages.defaultTo(new Stages(global)
            .addSimple("one", "vm01")
            .addSimple("two", "vm01, vm02")
            .addSimple("three", "vm01, vm02, vm03"));

        // this defines the deployment task
        deployProject = deployment.newBuilder()
            .CheckoutFiles_2({ _, task, input -> _.run(global.tasks.vcsUpdate); } as TaskCallable)
            .BuildAndCopy_3({ _, task, input -> _.run(nodeJs.build, copyConfiguration); } as TaskCallable)
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

    def copyConfiguration = new TaskDef<Task>({ SessionContext _, task, input ->
        final String dir = _.var(releases.pendingRelease).path + "/config"

        _.sys.copy(cp("config.example.js", "config.js").cd(dir).force()).throwIfError();
        _.sys.copy(cp("imager.example.js", "imager.js").cd(dir).force()).throwIfError();

        OK
    } as TaskCallable);

    @Override
    GridBuilder defaultGrid()
    {
        return newGrid().add(deployProject);
    }

    // main, can be run directly from an IDE
    static main(def args)
    {
        deploy()
    }

    // deploy script
    static deploy()
    {
        def demo = new NodeExpressMongooseDemoProject()
        demo
            .set(demo.main().propertiesFile, new File(".bear/express-demo.properties"))
            .configure()
            .set(demo.bear.stage, "three")
            .newGrid()
            .add(demo.deployProject)
            .run()
    }

    // setup script
    static setup()
    {
        def demo = new NodeExpressMongooseDemoProject()

        demo
            .set(demo.main().propertiesFile, new File(".bear/express-demo.properties"))
            .configure()
            .set(demo.bear.stage, "three")
            .set(demo.bear.verifyPlugins, true)
            .set(demo.bear.autoInstallPlugins, true)
            .set(demo.bear.checkDependencies, true)
            .newGrid()
            .add(demo.global.tasks.setup)
            .run()
    }
}