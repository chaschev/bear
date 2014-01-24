# Welcome the Bear!

Bear is a lightweight remote automation tool for Groovy/Java/JVM. Bear allows your to deploy projects, setup your cluster and install software to your remote machines. Bear differs from other existing tools in that it's using programmatic approach. In Bear your deployment is a regular Java class which may have it's `main()`. Bear loves static type safety, chained method calls, FP and fluent programming techniques. 

Below is an example of a remote task:

```groovy
@Project(shortName = "my")
class MyProject{
    
    @Method
    def sayHi(){
        run([named("say-hi task", { _, task ->
            _.sys.copy('foo').to('bar').run().throwIfError();
            
            println "${_.host}: ${_.sys.capture('echo hi from `hostname`')}!";
        } as TaskCallable)])
    }
    
    static main(args){
        new MyProject().sayHi()
    }
}
```
Which can be run as a Java app or from a command line:
```sh
$ bear my.sayHi --ui
```

Bear provides tested deployment examples which include deploying, starting and monitoring, release management, installing server application as a service for the following technologies:

* Node.js [[Source 1 - Drywall Demo]](https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/nodejs/DrywallDemoProject.groovy) [[Source 2 - Express/Express Demo]](https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/nodejs/NodeExpressMongooseDemoProject.groovy)
* Play! Framework [[Source - Secure Social Demo for MongoDB/MySQL]](https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/java/SecureSocialDemoProject.groovy)
* Grails/Tomcat [[Source - Grails Petclinic Demo]](https://github.com/chaschev/bear/blob/master/src/test/groovy/examples/java/GrailsTomcatDemoProject.groovy)

### Quick Start

To quickly start using Bear, check out the [Quick Start Guide](https://github.com/chaschev/bear/wiki/1.1.1.-Demo.-List-a-remote-dir). There also might be an interesting topic to read in our [Wiki](https://github.com/chaschev/bear/wiki). There are [demos and examples](https://github.com/chaschev/bear/wiki/1.1.3.-Node.js%2C-Grails%2C-Tomcat%2C-Play-and-other-demos) to use as prototypes for your own projects. These demos are also used as integration tests. At the moment Bear supports Node.js, Grails, Play! Framework.

### The Goal

The goal of Bear is to provide less learning experience by using features of the existing Java IDEs. Bear tries to minimize reading documentation and proposes using code completion or quick method lookup to create your deployment project.

The first version of Bear has been released on January 12th, 2014 and is a work in progress. It first started as a Capistrano clone, but then grew into a different project.

Our main priorities for the project for now are usability and bugfixing, so your feedback, bugreports and feature requests are very welcome. 

### Contacts

You can [create a ticket or ask a question](https://github.com/chaschev/bear/issues) or just drop a line at chaschev@gmail.com. We will accept contributions - below you will find instructions on how to build Bear and how to start the integration tests. Please discuss your changes with us if you want to change or add something significant.

### Alternatives

Bear can be compared to the following tools:

* [Puppet](http://puppetlabs.com/)
* [Chef](http://www.getchef.com/chef/)
* [Capistrano](http://capistranorb.com/)
* [Fabric](http://docs.fabfile.org/en/1.8/)
* [Salt](http://www.saltstack.com/)
* [Flynn](https://flynn.io/)
* [Ansible](http://www.ansibleworks.com/)

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

### Installing and using Bear

To install the latest stage version of Bear, type in your console:

```sh
$ mvn com.chaschev:installation-maven-plugin:1.4:install -Dartifact=com.chaschev:bear
```
    
Bear is also available in Maven Central:
```xml
<dependency>
    <groupId>com.chaschev</groupId>
    <artifactId>bear</artifactId>
    <version>1.0.2</version>
</dependency>
```

[Continue reading in Wiki...](https://github.com/chaschev/bear/wiki)

### Building Bear

Bear requires Maven 3.x to build:

```sh
$ mvn com.zenjava:javafx-maven-plugin:2.0:fix-classpath
$ git clone https://github.com/chaschev/bear.git
$ cd bear
$ mvn install
```

The first command will fix JavaFX installation to be available on classpath. You might need to run this with admin user. [More...](http://zenjava.com/javafx/maven/fix-classpath.html)

[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/chaschev/bear/trend.png)](https://bitdeli.com/free "Bitdeli Badge")
