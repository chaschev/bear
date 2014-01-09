# Welcome the Bear!

Bear is a lightweight remote automation tool for Java. Bear differs from other existing deployment tools by using OOP, static types and fluent programming techniques. It first started as a Capistrano clone, but then grew into a different project.

Bear has been released on January 4th, 2014 and is considered being alpha quality. It contains several integration tests which deploy popular projects hosted on Github written with using different programming languages and technologies - i.e Node.js, Grails, Play! Framework. You will find instructions in the [Quick Start Guide](https://github.com/chaschev/bear/wiki/2.1.1.-Creating-and-running-a-new-project).

Questions, concerns, feature requests? Just drop me a line at chaschev@gmail.com.

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

### Quick Start 

#### Prerequisites

In this tutorial you'll learn how to prepare your environment for running Bear projects.

* A remote Unix machine with standard password authentication. Ubuntu and CentOS are supported. A clean installation of these should be just fine.
* JDK 6 installed. JDK 7+ is required to run the UI, JDK 8+ is recommended to run the UI as it contains bugfixes and runs Nashorn which won't be there in JDK 7. [[Get Java 7]](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html) [[Get Java 8]](https://jdk8.java.net/download.html)
* Maven 3+. `mvn` must be available in the command line. [How to install Maven on Windows](http://www.mkyong.com/maven/how-to-install-maven-in-windows/).
 
#### Running the demo: list a remote directory

First, install Bear by running (admin rights might be necessary) in your command line:

```sh
$ mvn com.chaschev:installation-maven-plugin:1.4:install -Dartifact=com.chaschev:bear
```
   
Then, in your existing project which you want to deploy or just in an empty folder:

```sh
$ cd my-project
$ bear --create my --user my-actual-ssh-user --password my-actual-password --host my-remote-host
```
    
This will create a folder `.bear` with an auto-generated project. Note: password storage is unsafe in the current version. If you want to store your password locally in a file, you might want to edit `.bear/my.properties` file.

```sh
Created project file: .bear\MyProject.groovy
Created Maven pom: .bear\pom.xml
```
    
To quickly check the setup, run:

```sh
$ bear my.ls
```
    
The UI should launch and on the `my-remote-host` you should find the list of your remote directories.

![Configuration Sample][uiLs]

[uiLs]: https://raw.github.com/chaschev/bear/master/doc/img/bear-ui-ls.png

Command line `bear my.ls` simply runs the predefined method `ls()` in the generated project:

```groovy
@Method
def ls(){
    run([named("ls task", { _,  task ->
        println _.sys.lsQuick(".")
    } as TaskCallable)])
}
```
    
This looks a bit cryptic at first site, but the IDE should guide you through all the syntax troubles. Everything in the example is static ("this is normal Java"), so jumping to a method declaraion/definition should work fine. For example, in Intellij Idea pressing `F4` jumps to a method definition, `Ctrl+Shift+Space` and `.` will give you completion suggestions and `Alt+Enter` will help you to convert anonymous class to a closure. `_` is a session context variable, Bear's entry point, similar to `$` in jQuery.

From now the preferred way is to import this `pom.xml` as a Java project in your favourite Java IDE.

#### Running smoke tests to check configuration

To check that your setup is ok, run in your command line:

```sh
$ bear --unpack-demos
```

Next, open a file `.bear/examples/demo/SmokeProject.groovy` and edit it's stages to reflect your environment. Then type

to run in console:

```sh
$ bear smoke.runTests -q
```

to run with UI:

```sh
$ bear smoke.runTests --ui
```

If in your output you see lines like:

```
command execution time: 44.2ms/command
finished: Stats{time: "8.8s", partiesArrived: 2, partiesOk: 2, partiesPending: 0, partiesFailed: 0}
```

then your setup is complete.

#### Running demo projects

Demo projects stored in `.bear/examples` can be used as a bootstrap for your own projects. Each of these demos can be run the same way as the smoke tests were run. They all use open source projects stored at Github and should require only changes made to the stages.

Example:

```sh
$ bear drywall.setup  --ui
$ bear drywall.deploy --ui
```

TODO: add reference on deployment management quick start.

#### Notes

Tip for advanced users: you can also add Bear as a regular Maven dependency and use it as a jar. Your deployments can be run by `new MyProject().myDeployMethod()` - all needed configuration will be read from class annotations or from the environment variables and property files.

In case you want to install the latest development version, add `-Dshapshots=true -U` flags to the Maven command line above.


### Project Samples

Each deployment project consists of basically these parts:

* Plugins configuration. `BearProject::configureMe` - i.e. add NodeJs or Play! framework plugin, bind project source root to VCS folder, etc.
* Adding tasks. Tasks are pieces of deployments which can be reused, i.e. 'run Ant task (Java)' or 'run Grunt task (JS)' or 'run rake (Ruby)'.
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

Bear uses a computing grid framework under the hood which is invisible to the user by default. If you at some moment would require to utilize any of it's features to coordinate hosts session, i.e. to speed up a big file download by downloading it on a single host and sharing it among others, you could manually access the Grid API.

Computing Grid is an execution table for the tasks. It is a parallel framework which allows syncing tasks execution, communicating, sharing their results. Any task is run inside a grid and can be synchronized by getting a Future for some other cell in a grid.

### Installing and using Bear (latest developer release)

Bear is designed to be used as a normal Java library. A possible way to run it's projects is to have it on classpath and do `new YourBearProject().deploy()`.

To install the latest stage version of Bear, type in your console:

    mvn com.chaschev:installation-maven-plugin:1.4:install -Dartifact=com.chaschev:bear -Dshapshots=true -U

This command requires Maven 3.1.1+ installed and also might require administrator rights to create shortcuts in JAVA_HOME.

Note: installation-maven-plugin might not yet be in Maven Central at the time of writing.

To create a new Bear project after installation type

    bear --create project-name

This will create a .bear folder in which there will be a `pom.xml` and a simple project definition. In `.bear/project-name.properties` configuration can be defined, i.e. SSH authentication data. `.bear/pom.xml` project can be imported as a Maven module to an IDE of your choice and after it `.bear/ProjectNameProject.groovy` is ready to be run.

### Building Bear

Bear requires Maven 3.x to build:

    git clone https://github.com/chaschev/bear.git
    cd bear
    mvn com.zenjava:javafx-maven-plugin:2.0:fix-classpath
    mvn install

Third command will fix JavaFX installation to be available on classpath. You might need to run this with admin user. [More...](http://zenjava.com/javafx/maven/fix-classpath.html)

### Bear UI

Bear has a UI written in AngularJS inside a JavaFX's WebView. It's probably the first AngularJS desktop app. :-) It has  a code editor with code completion for script editing and many panes and triggers to monitor deployment execution over your hosts.

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