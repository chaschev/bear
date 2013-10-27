# Welcome the Bear!

Bear is a lightweight deployment tool for Java. It's primary task are builds and remote deploys of anything to your hosts. At the moment Tomcat, Grails and Maven are supported. Bear first started as a Capistrano clone, but then grew into a different project.

Bear is in it's early development stages now. If have questions or concerns, feel free to ask them at [my blog](http://chaschev.blogspot.com).

## Bear deployment script

### Bear script

Below is an a example of a Bear script. Scripts are a work in progress and about to change.

```ruby

# this is a preconfigured stage of three machines
:set stage='three'

# switch to the groovy shell mode
# lines below will be treated as Groovy expressions
:use shell groovy

# this will execute expressions on the hosts (vs locally)
:set groovy.sendToHosts=true

# this is how you invoke tasks. Code completion is supported in the UI.
# mosts of the tasks are predefined in plugins (i.e. in Grails plugin)
# task is usually a sequence of shell commands
runner.runTask(tasks.deploy)
runner.runTask(tasks.restartApp)

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

### Bear UI

Bear has a UI written in AngularJS inside a JavaFX's WebView. It's probably the first AngularJS desktop app. :-) It has  a code editor with code completion for script editing and many panes and triggers to monitor deployment execution over your hosts.
