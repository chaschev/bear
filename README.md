# Welcome the Bear!

Bear is a lightweight remote automation tool for Java. Bear differs from other existing deployment tools by using OOP, static types and fluent programming techniques. It first started as a Capistrano clone, but then grew into a different project.

First version of Bear has been released on January 12th, 2014 and is a work in progress.

To quickly start using the Bear, check out the [Quick Start Guide](https://github.com/chaschev/bear/wiki/1.1.1.-Demo.-List-a-remote-dir).

You may find an interesting topic to read in our [Wiki](https://github.com/chaschev/bear/wiki).

Bear has [demos and examples](https://github.com/chaschev/bear/wiki/1.1.3.-Node.js%2C-Grails%2C-Tomcat%2C-Play-and-other-demos) to use as prototypes for your own projects. These demos are also integration tests which are used to test it. At the moment Bear supports Node.js, Grails, Play! Framework.

The main priorities for project are now usability and bugfixing, so your feedback, bugreports and feature requests are very welcome. You can [create a ticket or ask a question](https://github.com/chaschev/bear/issues) or just drop me a line at chaschev@gmail.com. We will accept contributions, please discuss your changes with us if you want to change or add something significant.

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

There is [more on this in Wiki](https://github.com/chaschev/bear/wiki).

### Building Bear

Bear requires Maven 3.x to build:

    git clone https://github.com/chaschev/bear.git
    cd bear
    mvn com.zenjava:javafx-maven-plugin:2.0:fix-classpath
    mvn install

Third command will fix JavaFX installation to be available on classpath. You might need to run this with admin user. [More...](http://zenjava.com/javafx/maven/fix-classpath.html)

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