package examples.java
import bear.annotations.Configuration
import bear.annotations.Project
import bear.core.*
import bear.plugins.DeploymentPlugin
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DbDumpManager
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.grails.GrailsPlugin2
import bear.plugins.java.JavaPlugin
import bear.plugins.java.TomcatPlugin
import bear.plugins.maven.MavenPlugin
import bear.plugins.mysql.MySqlPlugin
import bear.task.TaskCallable
import bear.vcs.GitCLIPlugin

import static bear.plugins.db.DumpManagerPlugin.DbType.mongo
import static bear.task.NamedCallable.named
import static bear.task.TaskResult.OK
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Project(shortName = "petclinic", name = "Grails Petclinic Demo Deployment")
@Configuration(
    properties = ".bear/demos",
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
            .StartService_6({_, task -> _.run(tomcat.deployWar, tomcat.start, tomcat.watchStart); } as TaskCallable)
            .endDeploy()
            .ifRollback()
            .beforeLinkSwitch({_, task -> _.run(tomcat.stop); } as TaskCallable)
            .afterLinkSwitch({_, task -> _.run(tomcat.start, tomcat.watchStart); } as TaskCallable)
            .endRollback()

        return global;
    }


    static main(def args)
    {
        new GrailsTomcatDemoProject().stop()
    }

    def dumpSampleGrid(){
        run([named("", {_, task ->
            final DbDumpManager.DbService dumpService = _.var(dumpManager.dbService)

            final DbDumpInfo dump = dumpService.createDump("ss_demo")

            final List<DbDumpInfo> dumps = dumpService.listDumps()

            println "created dump $dump, rolling back to ${dumps[0]}"

            dumpService.restoreDump(dumps[0])

            println dumpService.printDumpInfo(dumpService.listDumps())
        } as TaskCallable)]);
    }
}
