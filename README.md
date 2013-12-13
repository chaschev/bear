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
| Node.js demo deployment                      | In progress... |
| Play! Framework three-hosts deployment       |                |
| Grails/Tomcat demo deployment                |                |
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

### Configuration Sample

Configuration is something which is shared between scripts. It defines versions of the tools, dependencies and deployment. I'm attaching this as a screenshot, because lambdas in Java 6 out of IDE are quite verbose.

![Configuration Sample][confSample]

[confSample]: https://raw.github.com/chaschev/bear/master/doc/bear-settings.png

### Bear UI

Bear has a UI written in AngularJS inside a JavaFX's WebView. It's probably the first AngularJS desktop app. :-) It has  a code editor with code completion for script editing and many panes and triggers to monitor deployment execution over your hosts.
