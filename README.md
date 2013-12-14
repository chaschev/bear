# Welcome the Bear!

Bear is a lightweight deployment tool for Java. It's primary task are builds and remote deploys of anything to your hosts. At the moment Tomcat, Grails and Maven are supported. Bear first started as a Capistrano clone, but then grew into a different project.

Bear is in it's early development stages now. Questions, concerns? Just drop me a line at chaschev@gmail.com.

### Bear Highlights

* Syntax completion in IDEs, static types and OOP approach for tasks, enum-like variables prevent typos
* Dynamic scripting with Groovy (@CompileStatic for strict Java-like mode)
* Debugging in IDEs and script unit-testing
* Fast execution, parallel execution framework
* Desktop UI app to monitor running tasks (Twitter Bootstrap 3)
* Scripts can be edited in UI and in IDEs (by importing through Maven)
* Lambda-based configuration, session/global context for variables
* (planned) Ruby, JavaScript and Python support.
* Takes some of the ideas from Capistrano

### Road Map for Release 1.0a1 (for CentOS 6.4)

| Step                                         | State          |
| -------------------------------------------- |:--------------:|
| Git, MongoDB and MySQL plugins               | Finished.      |
| Install as services (Upstart)                | Finished.      |
| Play! Framework single-host deployment       | Finished.      |
| Parametrize demo deployment (mongo or mysql) | Finished.      |
| CLI version                                  | Finished.      |
| Test deployment rollbacks and db dumps       | Finished.      |
| Node.js demo deployment - Drywall            | Finished.      |
| Node.js demo deployment - ExpressMongoose    | Finished.      |
| Grails/Tomcat demo deployment                | In Progress... |
| Play! Framework three-hosts deployment       |                |
| Node.js three-hosts deployment               |                |
| Refactoring, simplifying API                 | In progress... |
| Unit test coverage                           |                |
| UI bugfixing                                 |                |
| Installer                                    |                |
| Refactoring, simplifying API                 |                |


### Road Map for Release 1.0a2 (+ Ubuntu Server, Cloud)

| Step                                              | State          |
| ------------------------------------------------- |:--------------:|
| Support Debian/Ubuntu Server                      |                |
| Run via a Maven Plugin                            |                |
| Support JDK 6 for CLI, JDK 7 for GUI              |                |
| Deployments to Heroku, AWS, GCE, Azure, Rackspace |      ?       |

### Project Samples

Each deployment project consists of basically these parts:

* Plugins configuration. `BearProject::configureMe` - i.e. add NodeJs or Play! framework plugin, bind project source root to VCS folder, etc.
* Adding tasks. Tasks are pieces of deployments which can be reused, i.e. 'run Ant task (Java)' or 'run Grunt task (JS)' or 'run rake (Ruby)'.
* Defining scripts. Scripts are collections of tasks which are most commonly used, i.e. setup script, deploy script db dump or restore script. The goal is to run each scenario with minimal action.

Deployment project examples are available under the [examples folder][examplesFolder].

* [node-express-mongoose-demo][NodeExpressMongooseDemoProject] Stack: Node.js, Express, MongoDB.
* [Secure Social deployment][SecureSocialDemoProject] Stack: Java, Play! Framework 2, MySQL/MongoDB, Secure Social.

[NodeExpressMongooseDemoProject]: https://github.com/chaschev/bear/blob/master/src/main/groovy/examples/node/NodeExpressMongooseDemoProject.groovy
[SecureSocialDemoProject]: https://github.com/chaschev/bear/blob/master/src/main/groovy/examples/java/SecureSocialDemoProject.groovy 


```properties
# express-demo.properties

bear.name=express-demo
bear.fullName=Node Express Mongoose Demo Deployment
bear.repositoryURI=git@github.com:madhums/node-express-mongoose-demo.git
bear.vcsBranchName=master
bear.sshUsername=andrey
bear.sshPassword=***
bear.stage=one
```

```groovy
// NodeExpressMongooseDemoProject.groovy

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

    // a helper task to copy configuration
    TaskDef<Task> copyConfiguration = new TaskDef<Task>({ SessionContext _, task, Object input ->
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
        deploy(new NodeExpressMongooseDemoProject())
    }

    // deploy script
    static deploy(NodeExpressMongooseDemoProject demo)
    {
        demo
            .set(demo.main().propertiesFile, new File(".bear/express-demo.properties"))
            .configure()
            .newGrid()
            .add(demo.deployProject)
            .run()
    }

    // setup script
    static setup(NodeExpressMongooseDemoProject demo)
    {
        demo
            .set(demo.main().propertiesFile, new File(".bear/express-demo.properties"))
            .configure()
            .set(demo.bear.verifyPlugins, true)
            .set(demo.bear.autoInstallPlugins, true)
            .set(demo.bear.checkDependencies, true)
            .newGrid()
            .add(demo.global.tasks.setup)
            .run()
    }
}
```

There are also grids. Grid is an execution table for the tasks. It is a parallel framework which allows syncing tasks execution, communicating, sharing their results (i.e. a file downloaded). Any task is run inside a grid and can be synchronized by getting a Future for some other cell in a grid. If your tasks a simple and you don't need syncing, grid cold be seen as an array for your tasks.

[examplesFolder]: https://github.com/chaschev/bear/tree/master/src/main/groovy/examples

### Bear UI

Bear has a UI written in AngularJS inside a JavaFX's WebView. It's probably the first AngularJS desktop app. :-) It has  a code editor with code completion for script editing and many panes and triggers to monitor deployment execution over your hosts.
