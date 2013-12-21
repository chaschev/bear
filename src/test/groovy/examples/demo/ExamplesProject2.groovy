package examples.demo
import bear.annotations.Configuration
import bear.annotations.Project
import bear.core.*
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.java.JavaPlugin
import bear.plugins.java.PlayPlugin
import bear.plugins.maven.MavenPlugin
import bear.plugins.mongo.MongoDbPlugin
import bear.plugins.mysql.MySqlPlugin
import bear.task.TaskCallable
import bear.vcs.GitCLIPlugin

import static bear.session.BearVariables.joinPath
import static bear.task.TaskResult.OK
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Project(shortName = "examples-demo2", name = "Command Examples Demo 2")
@Configuration(
    propertiesFile = ".bear/ss-demo",
    stage = "three",
    vcs = "git@github.com:chaschev/securesocial.git",
    branch = "master",
    useUI = false
)
public class ExamplesProject2 extends BearProject<ExamplesProject2> {
    // these are the plugins which are injected
    JavaPlugin java;
    MavenPlugin maven;
    Bear bear;
    GitCLIPlugin git;
    PlayPlugin play;
    MongoDbPlugin mongoPlugin;
    MySqlPlugin mysqlPlugin;
    DumpManagerPlugin dumpManager;

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        maven.version.set("3.0.5");

        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        play.version.set("2.2.0");

        play.projectPath.setEqualTo(joinPath(bear.vcsBranchLocalPath, "samples/java/db-demo"));

        bear.stages.set(new Stages(global)
            .addSimple("one", "vm01")
            .addSimple("two", "vm01, vm02")
            .addSimple("three", "vm01, vm02, vm03"));

        return global;
    }


    static main(args)
    {
        new ExamplesProject2()
            .run(
            [
                {_, task, i -> println "${_.sys.name}: ${_.sys.fileSizeAsLong('/home/andrey/texty')}"; OK} as TaskCallable,
                {_, task, i -> println "${_.sys.name}: ${_.sys.capture('cat /home/andrey/texty')}"; OK} as TaskCallable
            ])
    }
}
