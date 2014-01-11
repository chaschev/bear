# Welcome the Bear!

Bear is a lightweight remote automation tool for Java. Bear differs from other existing deployment tools by using OOP, static types and fluent programming techniques. It first started as a Capistrano clone, but then grew into a different project.

First version of Bear has been released on January 12th, 2014 and is a work in progress.

To quickly start using the Bear, check out the [Quick Start Guide](https://github.com/chaschev/bear/wiki/1.1.1.-Demo.-List-a-remote-dir).

You may find an interesting topic to read in our [Wiki](https://github.com/chaschev/bear/wiki).

Bear has demos and examples to use as prototypes for your own projects. These demos are also integration tests which are used to test it. At the moment Bear supports Node.js, Grails, Play! Framework.

The main priorities for project are now usability and bugfixing, so your feedback, bugreports and feature requests are very welcome. [Create a ticket or ask a question](https://github.com/chaschev/bear/issues) or just drop me a line at chaschev@gmail.com.

### Bear Highlights

* Syntax completion in IDEs, static type safety and OOP approach (see demos below)
* Dynamic scripting with Groovy (@CompileStatic for strict Java-like mode)
* Debugging in IDEs and (planned) script unit-testing
* Fast, parallel execution framework
* Desktop UI app to monitor running tasks (Twitter Bootstrap 3)
* A single project definition file driven by annotations and convention over configuration
* Scripts can be edited in UI and in IDEs (by importing through Maven)
* Configuration is driven by variables any of which can be redefined globally or for a single host.
* Technologies supported: Node.js, Grails, Play! Framework 2, Tomcat, upstart scripts.
* (planned) JavaScript, Ruby and Python support
* Takes some of the ideas from Capistrano

### Bear UI

Bear has a UI written in AngularJS inside a JavaFX's WebView. It's probably the first AngularJS desktop app. :-)

![Configuration Sample][uiLs]

[uiLs]: https://raw.github.com/chaschev/bear/master/doc/img/bear-ui-ls.png

### Project Samples

Each deployment project consists of basically these parts:

* Plugins configuration. `BearProject::configureMe` - i.e. add NodeJs or Play! framework plugin, bind project source root to VCS folder, etc.
* Adding tasks. Tasks are pieces of deployments which can be reused, i.e. 'copying a file with Ant in Java' or 'running Grunt task in JS' or 'running rake in Ruby'.
* Defining scripts. Scripts are collections of tasks which are most commonly used, i.e. setup script, deploy script db dump or restore script. The goal is to run each scenario with minimal action.

Deployment project examples are available under the [examples folder][examplesFolder].

* [node-express-mongoose-demo deployment][NodeExpressMongooseDemoProject] Stack: Node.js, Express, MongoDB.
* [Secure Social deployment][SecureSocialDemoProject] Stack: Java, Play! Framework 2, MySQL/MongoDB, Secure Social.
* [Shell API basic usage][BasicExamplesDemoProject]

[NodeExpressMongooseDemoProject]: https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/nodejs/NodeExpressMongooseDemoProject.groovy
[SecureSocialDemoProject]: https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/java/SecureSocialDemoProject.groovy
[BasicExamplesDemoProject]: https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/demo/ExamplesProject.groovy
[examplesFolder]: https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/demo/

```groovy
// NodeExpressMongooseDemoProject.groovy

@Project(shortName =  "express-demo", name = "Node Express Mongoose Demo Deployment")
@Configuration(
    propertiesFile = ".bear/express-demo",
    stage = "three",
    vcs = "git@github.com:madhums/node-express-mongoose-demo.git",
    branch = "master",
    useUI = false
)
public class NodeExpressMongooseDemoProject extends BearProject<NodeExpressMongooseDemoProject> {
    // these are the plugins which are injected
    Bear bear;
    GitCLIPlugin git;
    NodeJsPlugin nodeJs;
    MongoDbPlugin mongoPlugin;
    DeploymentPlugin deployment;
    ReleasesPlugin releases;
    DumpManagerPlugin dumpManager;

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        nodeJs.version.set("0.10.22");
        nodeJs.appCommand.set("server.js")
        nodeJs.projectPath.setEqualTo(bear.vcsBranchLocalPath);

        nodeJs.instancePorts.set("5000, 5001")

        dumpManager.dbType.set(mongo.toString());

        nodeJs.configureService.setDynamic({ SessionContext _ ->
            return { ConfigureServiceInput input ->
                input.service
                    .cd(_.var(releases.activatedRelease).get().path)
                    .exportVar("PORT", input.port + "");

                return null;
            } as Function;
        } as Fun);

        bear.stages.set(new Stages(global)
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

    def copyConfiguration = new TaskDef<Task>({ SessionContext _, task, input ->
        final String dir = _.var(releases.pendingRelease).path + "/config"

        _.sys.copy(cp("config.example.js", "config.js").cd(dir).force()).throwIfError();
        _.sys.copy(cp("imager.example.js", "imager.js").cd(dir).force()).throwIfError();

        OK
    } as TaskCallable);

    // main, can be run directly from an IDE
    static main(def args)
    {
        // complete deployment:
        // checkout, build, stop, copy code to release, start
        // inspect startup logs, update upstart scripts
        new NodeExpressMongooseDemoProject().deploy()

        //stop all 6 instances (3 VMs, 2 instances each)
        new NodeExpressMongooseDemoProject().stop()

        //start all 6 instances
        new NodeExpressMongooseDemoProject().start()
    }
}
```

Bear uses a computing grid framework under the hood, [Read More...](https://github.com/chaschev/bear/wiki/3.5.-Parallel-execution-framework)

### Installing and using Bear (latest developer release)

To install the latest stage version of Bear, type in your console:

    mvn com.chaschev:installation-maven-plugin:1.4:install -Dartifact=com.chaschev:bear

There is [more on this in Wiki](https://github.com/chaschev/bear/wiki).

### Building Bear

Bear requires Maven 3.x to build:

    git clone https://github.com/chaschev/bear.git
    cd bear
    mvn com.zenjava:javafx-maven-plugin:2.0:fix-classpath
    mvn install

Third command will fix JavaFX installation to be available on classpath. You might need to run this with admin user. [More...](http://zenjava.com/javafx/maven/fix-classpath.html)



### Road Map for Release 1.0a1 (for CentOS 6.4)

| Step                                         | State          |
| -------------------------------------------- |:--------------:|
| Git, Upstart, MongoDB and MySQL plugins      | Finished.      |
| Test deployment rollbacks and db dumps       | Finished.      |
| Node.js deployment demo - Drywall, ExpressMongoose  | Finished.      |
| Grails/Tomcat deployment demo                | Finished.      |
| Node.js/Java three-hosts deployment          | Finished.      |
| Installer, launcher                          | Finished.      |
| Support Ubuntu                               | Finished.      |
| Integration & unit tests                     | Finished.      |
| UI bugfixing                                 | Finished.      |
| Refactoring, simplifying API                 | Finished.      |
| Finishing TODOs                              | Finished.      |
| Quick Start Tutorial                         | Finished.      |


### Road Map for Release 1.0a2 (+ Ubuntu Server, localhost, Cloud)

| Step                                              | State          |
| ------------------------------------------------- |:--------------:|
| Support localhost                                 |                |
| JavaScript, sample project                        |                |
| Ruby, RoR demo, sample project                    |                |
| Python & sample project & demo                    |                |
| Deployments to Heroku, AWS, GCE, Azure, Rackspace |                |
| Run via a Maven Plugin                            |                |
| Support JDK 6 for CLI, JDK 7 for GUI              |                |
| Documentation                                     |                |