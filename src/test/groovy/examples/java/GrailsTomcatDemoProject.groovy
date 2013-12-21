package examples.java

import bear.core.*
import bear.plugins.ConfigureServiceInput
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DbDumpManager
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.grails.GrailsPlugin2
import bear.plugins.java.JavaPlugin
import bear.plugins.java.TomcatPlugin
import bear.plugins.maven.MavenPlugin
import bear.plugins.misc.UpstartPlugin
import bear.plugins.mysql.MySqlPlugin
import bear.plugins.DeploymentPlugin
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.vcs.GitCLIPlugin
import com.google.common.base.Function

import static bear.plugins.db.DumpManagerPlugin.DbType.mongo
import static bear.task.TaskResult.OK

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

public class GrailsTomcatDemoProject extends BearProject<GrailsTomcatDemoProject> {
    // these are the plugins which are injected
    JavaPlugin java
    MavenPlugin maven
    Bear bear
    GitCLIPlugin git
    MySqlPlugin mysqlPlugin
    DeploymentPlugin deployment
    DumpManagerPlugin dumpManager
    TomcatPlugin tomcat
    GrailsPlugin2 grails
    UpstartPlugin upstart

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        maven.version.set("3.0.5");

        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        grails.version.set("2.1.1")

        tomcat.instancePorts.set("8080, 8081")
        tomcat.warName.setEqualTo(grails.warName);

        dumpManager.dbType.set(mongo.name());

        bear.appStartTimeoutSec.set(600)

        bear.stages.set(new Stages(global)
            .addSimple("one", "vm01")
            .addSimple("two", "vm01, vm02")
            .addSimple("three", "vm01, vm02, vm03"));

        // this defines the deployment task
        defaultDeployment = deployment.newBuilder()
            .CheckoutFiles_2({ _, task, input -> _.run(global.tasks.vcsUpdate); } as TaskCallable)
            .BuildAndCopy_3({ _, task, input -> _.run(grails.build); } as TaskCallable)
            .StopService_5({ _, task, input -> _.run(tomcat.stop); OK; } as TaskCallable)
            .StartService_8({ _, task, input -> _.run(updateUpstart, tomcat.deployWar, tomcat.start, tomcat.watchStart); } as TaskCallable)
            .endDeploy()
            .ifRollback()
            .beforeLinkSwitch({ _, task, input -> _.run(tomcat.stop); } as TaskCallable)
            .afterLinkSwitch({ _, task, input -> _.run(tomcat.start, tomcat.watchStart); } as TaskCallable)
            .endRollback()

        return global;
    }


    // a helper upstart task which updates a port in a tomcat upstart script
    // not used, left for reference only
    // it recreates tomcat upstart scripts from scratch by reusing tomcat's upstart implementation
    // may be there is a more simple way to do this with tomcat...
    def updateUpstart = new TaskDef<Task>({ SessionContext _, task, input ->
        def defaultUpstartConfigurator = tomcat.newBasicUpstartConfigurator(_)

        _.putConst(tomcat.createScriptText, tomcat.newBasicUpstartScriptText(_))

        _.putConst(tomcat.configureService, { ConfigureServiceInput serviceInput ->
            defaultUpstartConfigurator.apply(serviceInput)

            int diff = serviceInput.port.toInteger() - 8080

            serviceInput.service.exportVar("ACTIVE_MQ_PORT", "" + (61616 + diff))

            return null;
        } as Function)

        return _.runSession(
            upstart.create.singleTaskSupplier().createNewSession(_, task, upstart.create),
            _.var(tomcat.customUpstart)
        );
    } as TaskCallable)

    static main(def args)
    {
        deploy()
    }

    static deploy()
    {
        def demo = new GrailsTomcatDemoProject()

        demo
            .set(demo.main().propertiesFile, new File(".bear/grails-petclinic-demo.properties"))
            .configure()
            .set(demo.bear.clean, false)
            .newGrid()
            .add(demo.defaultDeployment.build())
            .runCli();
    }

    // setup script
    static setup()
    {
        def demo = new GrailsTomcatDemoProject()

        demo
            .set(demo.main().propertiesFile, new File(".bear/grails-petclinic-demo.properties"))
            .configure()
            .set(demo.bear.verifyPlugins, true)
            .set(demo.bear.autoInstallPlugins, true)
            .set(demo.bear.checkDependencies, true)
            .newGrid()
            // a quick dirty hack to force the installation
//            .add({ SessionContext _, task, i -> _.sys.rm(RmInput.newRm('/var/lib/bear/tools/tomcat/6.0.37').sudo())} as TaskCallable)
            .add(demo.global.tasks.setup)
            .runCli()
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


}
