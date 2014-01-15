# Welcome the Bear!

Bear is a lightweight remote automation tool for Groovy/Java/JVM. Bear allows your to deploy projects, setup your cluster, install software to your remote machines. Bear differs from other existing tools in that it's using programmatic approach - your deployment is a regular Java class which may have it's `main()`. Bear also uses static types, chained method calls, FP and fluent programming techniques.

The goal of Bear is to provide less learning experience with help of the existing Java IDEs. Bear tries to minimize reading documentation and proposes using IDE features like code completion or quick method lookup to create your deployment project.

First version of Bear has been released on January 12th, 2014 and is a work in progress. It first started as a Capistrano clone, but then grew into a different project.

To quickly start using Bear, check out the [Quick Start Guide](https://github.com/chaschev/bear/wiki/1.1.1.-Demo.-List-a-remote-dir).

You may find an interesting topic to read in our [Wiki](https://github.com/chaschev/bear/wiki).

Bear has [demos and examples](https://github.com/chaschev/bear/wiki/1.1.3.-Node.js%2C-Grails%2C-Tomcat%2C-Play-and-other-demos) to use as prototypes for your own projects. These demos are also used as integration tests. At the moment Bear supports Node.js, Grails, Play! Framework.

Our main priorities for the project for now are usability and bugfixing, so your feedback, bugreports and feature requests are very welcome. You can [create a ticket or ask a question](https://github.com/chaschev/bear/issues) or just drop a line at chaschev@gmail.com. We will accept contributions - below you will find instructions on how to build Bear and how to start the integration tests. Please discuss your changes with us if you want to change or add something significant.

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

    mvn com.chaschev:installation-maven-plugin:1.4:install -Dartifact=com.chaschev:bear

[Continue reading in Wiki](https://github.com/chaschev/bear/wiki).

### Building Bear

Bear requires Maven 3.x to build:

    mvn com.zenjava:javafx-maven-plugin:2.0:fix-classpath
    git clone https://github.com/chaschev/bear.git
    cd bear
    mvn install

The first command will fix JavaFX installation to be available on classpath. You might need to run this with admin user. [More...](http://zenjava.com/javafx/maven/fix-classpath.html)

### Road Map for Release 1.0a2 (+ Ubuntu Server, localhost, Cloud)

| Step                                              | State          |
| ------------------------------------------------- |:--------------:|
| Support localhost                                 |                |
| Support JavaScript, sample project                        |                |
| Support Ruby, RoR demo, sample project                    |                |
| Support Python & sample project & demo                    |                |
| Deployments to Heroku, AWS, GCE, Azure, Rackspace |                |
| Run via a Maven Plugin                            |                |
| Support JDK 6 for CLI, JDK 7 for GUI              |                |
| Documentation                                     |                |
