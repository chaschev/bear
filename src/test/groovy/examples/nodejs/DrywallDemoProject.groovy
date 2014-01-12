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
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.vcs.GitCLIPlugin
import com.google.common.base.Function

import static bear.plugins.db.DumpManagerPlugin.DbType.mongo
import static bear.task.TaskResult.OK
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Project(shortName =  "drywall-demo", name = "Drywall Demo Deployment")
@Configuration(
    properties = ".bear/demos",
    stage = "u-3",
    vcs = "https://github.com/jedireza/drywall.git",
    branch = "master",
    useUI = true
)
public class DrywallDemoProject extends BearProject<DrywallDemoProject> {
    // these are the plugins which are injected
    GitCLIPlugin git
    NodeJsPlugin nodeJs
    MongoDbPlugin mongoPlugin
    DeploymentPlugin deployment
    ReleasesPlugin releases
    DumpManagerPlugin dumpManager

    public TaskDef deployProject;

    def copyConfiguration = new TaskDef({_, task ->
        final String dir = _.var(releases.pendingRelease).path

        if(!_.sys.exists(dir + "/config.js")){
            _.sys.move("config.example.js").to("config.js").inDir(dir).run().throwIfError();
        }

        OK
    } as TaskCallable);

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        nodeJs.version.set("0.10.22");
        nodeJs.projectPath.setEqualTo(bear.vcsBranchLocalPath);

        nodeJs.instancePorts.set("3000")

        dumpManager.dbType.set(mongo.toString());

        //todo ugly?
        nodeJs.configureService.setDynamic({SessionContext _ ->
            {ConfigureServiceInput input -> input.service.cd(_.var(releases.activatedRelease).get().path) } as Function
        } as Fun);

        nodeJs.createScriptText.setEqualTo(nodeJs.simpleGruntUpstart);

        bear.stages.set(new Stages(global)
            .addQuick("one", "vm01")
            .addQuick("two", "vm01, vm02")
            .addQuick("three", "vm01, vm02, vm03")
            .addQuick("u-1", "vm04")
            .addQuick("u-2", "vm04, vm05")
            .addQuick("u-3", "vm04, vm05, vm06")
        );

        // this defines the deployment task
        defaultDeployment = deployment.newBuilder()
            .CheckoutFiles_2({_, task -> _.run(global.tasks.vcsUpdate); } as TaskCallable)
            .BuildAndCopy_3({_, task -> _.run(nodeJs.build, copyConfiguration); } as TaskCallable)
            .StopService_4({_, task -> _.run(nodeJs.stop); OK; } as TaskCallable)
            .StartService_6({_, task -> _.run(nodeJs.start, nodeJs.watchStart); } as TaskCallable)
            .endDeploy()
            .ifRollback()
            .beforeLinkSwitch({_, task -> _.run(nodeJs.stop); } as TaskCallable)
            .afterLinkSwitch({_, task -> _.run(nodeJs.start, nodeJs.watchStart); } as TaskCallable)
            .endRollback();

        return global;
    }

    static main(args)
    {
        new DrywallDemoProject().start()
    }

    public GlobalTaskRunner setup()
    {
        global.tasks.setup.before({_, task -> _.sys.packageManager.installPackage("ImageMagick"); OK } as TaskCallable)

        super.setup()
    }
}