package examples.java

import bear.annotations.Configuration
import bear.annotations.Project
import bear.core.*
import bear.plugins.ConfigureServiceInput
import bear.plugins.DeploymentPlugin
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DbDumpManager
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.grails.GrailsPlugin2
import bear.plugins.java.JavaPlugin
import bear.plugins.java.TomcatPlugin
import bear.plugins.maven.MavenPlugin
import bear.plugins.misc.UpstartPlugin
import bear.plugins.mysql.MySqlPlugin
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

@Project(shortName = "petclinic", name = "Grails Petclinic Demo Deployment")
@Configuration(
    properties = ".bear/petclinic",
    stage = "u-3",
    useUI = false,
    vcs = "https://github.com/grails-samples/grails-petclinic.git",
    branch = "master",
    user = "andrey"
)
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
            .BuildAndCopy_3({_, task -> _.run(grails.build); } as TaskCallable)
            .StopService_4({_, task -> _.run(tomcat.stop); OK; } as TaskCallable)
            .StartService_6({_, task -> _.run(updateUpstart, tomcat.deployWar, tomcat.start, tomcat.watchStart); } as TaskCallable)
            .endDeploy()
            .ifRollback()
            .beforeLinkSwitch({_, task -> _.run(tomcat.stop); } as TaskCallable)
            .afterLinkSwitch({_, task -> _.run(tomcat.start, tomcat.watchStart); } as TaskCallable)
            .endRollback()

        return global;
    }


    // a helper upstart task which updates a port in a tomcat upstart script
    // not used, left for reference only
    // it recreates tomcat upstart scripts from scratch by reusing tomcat's upstart implementation
    // may be there is a more simple way to do this with tomcat...
    def updateUpstart = new TaskDef({ SessionContext _, task ->
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
        new GrailsTomcatDemoProject().stop()
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
