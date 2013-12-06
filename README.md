# Welcome the Bear!

Bear is a lightweight deployment tool for Java. It's primary task are builds and remote deploys of anything to your hosts. At the moment Tomcat, Grails and Maven are supported. Bear first started as a Capistrano clone, but then grew into a different project.

Bear is in it's early development stages now. Questions, concerns? Just to drop me a line at chaschev@gmail.com.

### Road Map for Release 1.0b1 (for CentOS 6.4)

| Step                                        | State          | 
| ------------------------------------------- |:--------------:|
| Git, MongoDB and MySQL plugins              | Finished.      |
| Install as services (Upstart)               | Finished.      | 
| Play! Framework single-host deployment      | Finished.      | 
| Play! Framework three-hosts deployment      | In progress... |
| Parametrize deployment (use mongo or mysql) | In progress... |
| Test deployment rollbacks and db dumps      | In progress... |
| Grails/Tomcat demo deployment               |                | 
| Node.js demo deployment                     |                | 
| Refactoring, simplifying API                |                | 
| Unit test coverage                          |                |
| UI bugfixing                                |                |
| Installer                                   |                |
| CLI version                                 |                | 
| Refactoring, simplifying API                |                | 



### Road Map for Release 1.0b2 (+ Ubuntu Server, Cloud)

| Step                                        | State          | 
| ------------------------------------------- |:--------------:|
| Support Debian/Ubuntu Server                |                |
| Run via a Maven Plugin                      |                |
| Support JDK 6 for CLI, JDK 7 for GUI        |                | 
| Deployments to Heroku, AWS, GCE, Azure, Rackspace |      ?       |

### Bear Script

Below is an a example of a Bear script. Scripts are a work in progress and about to change.

```ruby

# use a preconfigured stage of three machines
:set stage='three'

# switch to the groovy shell mode
# lines below will be treated as Groovy expressions
:use shell groovy

# this is how you invoke tasks. Code completion is supported in the UI.
# mosts of the tasks are predefined in plugins (i.e. in Grails plugin)
# task is usually a sequence of shell commands

:run {"task": "tasks.deploy"}
:run {"task": "tasks.restartApp"}

# switch to remote sh mode (distributed)
:use shell remote
ls -ltr /var/lib/myApp

# switch to sql mode (distributed)
:use shell mysql
show tables;

# switch to groovy (not distributed, command is run locally)
:use shell groovy
:set groovy.sendToHosts=false

frame = javax.swing.JFrame("close me!")
frame.add(javax.swing.JButton("click me!"))
frame.setSize(200, 100)
frame.visible = true

# run a mongodb script example

:use shell groovy {"name": "mongoTest"}
:set mongoDb.dbName {"value":"test"}

import bear.core.SessionContext
import bear.plugins.mongo.MongoDbPlugin

# this will enable code completion in IDE
def _ = ((SessionContext)_);

def mongo = _.getGlobal().getPlugin(MongoDbPlugin);

mongo.dbName.defaultTo("test")

def r = mongo.runScript(_, """
db = db.getSiblingDB('test');
printjson(db.system.users.find());
""")

ui.info("result: {}", r)
```

### Configuration Sample

Configuration is something which is shared between scripts. It defines versions of the tools, dependencies and deployment. I'm attaching this as a screenshot, because lambdas in Java 6 out of IDE are quite verbose.

![Configuration Sample][confSample]

[confSample]: https://raw.github.com/chaschev/bear/master/doc/bear-settings.png

### Bear UI

Bear has a UI written in AngularJS inside a JavaFX's WebView. It's probably the first AngularJS desktop app. :-) It has  a code editor with code completion for script editing and many panes and triggers to monitor deployment execution over your hosts.
