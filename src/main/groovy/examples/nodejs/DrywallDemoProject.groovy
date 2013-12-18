package examples.nodejs
import bear.annotations.Configuration
import bear.annotations.Project
import bear.context.Fun
import bear.core.*
import bear.plugins.ConfigureServiceInput
import bear.plugins.DeploymentPlugin
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.misc.ReleasesPlugin
import bear.plugins.mongo.MongoDbPlugin
import bear.plugins.nodejs.NodeJsPlugin
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.vcs.GitCLIPlugin
import com.google.common.base.Function

import static bear.plugins.db.DumpManagerPlugin.DbType.mongo
import static bear.plugins.sh.CopyOperationInput.mv
import static bear.task.TaskResult.OK
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Project(shortName =  "drywall-demo", name = "Drywall Demo Deployment")
@Configuration(
    propertiesFile = ".bear/drywall",
    stage = "three",
    vcs = "git@github.com:jedireza/drywall.git",
    branch = "master",
    useUI = false
)
public class DrywallDemoProject extends BearProject<DrywallDemoProject> {
    // these are the plugins which are injected
    GitCLIPlugin git
    NodeJsPlugin nodeJs
    MongoDbPlugin mongoPlugin
    DeploymentPlugin deployment
    ReleasesPlugin releases
    DumpManagerPlugin dumpManager

    public TaskDef<Task> deployProject;

    def copyConfiguration = new TaskDef<Task>({ SessionContext _, Task<TaskDef> task, Object input ->
        final String dir = _.var(releases.pendingRelease).path

        if(!_.sys.exists(dir + "/config.js")){
            _.sys.move(mv("config.example.js", "config.js").cd(dir)).throwIfError();
        }

        OK
    } as TaskCallable<TaskDef>);

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        nodeJs.version.set("0.10.22");
        nodeJs.projectPath.setEqualTo(bear.vcsBranchLocalPath);
        bear.vcsBranchName.defaultTo("master");

        nodeJs.instancePorts.set("3000")

        dumpManager.dbType.set(mongo.toString());

        //todo ugly?
        nodeJs.configureService.setDynamic({SessionContext _ ->
            {ConfigureServiceInput input -> input.service.cd(_.var(releases.activatedRelease).get().path) } as Function
        } as Fun);

        nodeJs.createScriptText.setEqualTo(nodeJs.simpleGruntUpstart);

        bear.stages.defaultTo(new Stages(global)
            .addSimple("one", "vm01")
            .addSimple("two", "vm01, vm02")
            .addSimple("three", "vm01, vm02, vm03"));

        // this defines the deployment task
        defaultDeployment = deployment.newBuilder()
            .CheckoutFiles_2({ _, task, input -> _.run(global.tasks.vcsUpdate); } as TaskCallable)
            .BuildAndCopy_3({ _, task, input -> _.run(nodeJs.build, copyConfiguration); } as TaskCallable)
            .StopService_5({ _, task, input -> _.run(nodeJs.stop); OK; } as TaskCallable)
            .StartService_8({ _, task, input -> _.run(nodeJs.start, nodeJs.watchStart); } as TaskCallable)
            .endDeploy()
            .ifRollback()
            .beforeLinkSwitch({ _, task, input -> _.run(nodeJs.stop); } as TaskCallable)
            .afterLinkSwitch({ _, task, input -> _.run(nodeJs.start, nodeJs.watchStart); } as TaskCallable)
            .endRollback();

        return global;
    }

    static main(args)
    {
        new DrywallDemoProject().stop()
    }

    public void setup()
    {
        global.tasks.setup.before({ _, task, i -> _.sys.packageManager.installPackage("ImageMagick"); OK } as TaskCallable)

        super.setup()
    }
}