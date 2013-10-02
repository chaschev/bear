# Ideas and user stories

### Why

If you look at the [Capistrano tag](http://stackoverflow.com/questions/tagged/capistrano) at Stackoverflow, you will notice that Java is almost never used with Capistrano though recommended to use as a universal deployment tool. Bear is there to fill this gap.

### Start a new project with remote resource deployments {#id-goes-here}

    $ bear --capify

This should create the [files](#bootstrap-files) for the project.

### Bootstrap files {#bootstrap-files}

`bear-settings.rb` / `BearSettings.java` - this is a typical project with Java, Maven and Git plugins included

`new-scripts.rb` / `NewScripts.java`

### Start a new Maven project from scratch ("can't recall that archetype command")

Add aliases to Maven commands?

    $ bear mvn:new

Or via console

    $ bear
    bear$ $.useShell(Maven)
    bear$ help
    bear$ new

### Run a script from project

### Shell mode for each of the plugins

If it's a wrapper around a tool, just pass argument to this tool and print the results. Brilliant!

+ Extended mode in a console to support additional commands. Or to leave enabled the context object. Think it over.

### Installing the Bear

    $ mvn bear:get
    $ mvn installer:install -Dartifact=com.chaschev:bear

### Editing scripts within an IDE

To benefit all advantages of static-compiled languages users could edit their scripts from IDEs. The simplest and the most universal way to achieve this is to create a `pom.xml` and then import it as a project.

Will this `pom.xml` contain a single dependency to Bear?

IDE will be used for code completion and syntax highlighting. And Bear will be used for running.


### Run commands from console

    bear$ $.useShell(Remote)

    bearsh$ use hosts ...
    bearsh@hosts$ pwd && ls

    bearsh$ use stage stage1
    bearsh@stage1$ pwd && ls

#### Run a command for multiple hosts

### Utilize Maven

[Link back to H2](#id-goes-here)

### Write a new plugin

### maven-installation-plugin vs Bear

## Implementation details

### To improve

- Settings should be imported into the script. There could be several settings possible and it's a good practise to use language features.
- Script must initialize the settings and plugins implicitly or not.
-


## Bear? WTF?

- The project started in Russia and Russia is famous for two things - vodka and bears drinking vodka
- Name Vodka is nice, but does not remind me of super-powers
- Saying "install it with bear" or "bear is my weapon of choice" sounds at least less awkward
