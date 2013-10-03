# Ideas and user stories

### Why

If you look at the [Capistrano tag](http://stackoverflow.com/questions/tagged/capistrano) at Stackoverflow, you will notice that Java is almost never used with Capistrano though recommended to use as a universal deployment tool. Bear is there to fill this gap.

### <a id="id-goes-here">Start a new project with remote resource deployments

    $ bear --capify

This should create the [files](#bootstrap-files) for the project.

### <a id="bootstrap-files"></a>Bootstrap files

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

### Installing Bear

    $ mvn bear:get
    $ mvn installer:install -Dartifact=com.chaschev:bear

### Editing scripts within an IDE

To benefit all the advantages of static-compiled languages users could edit their scripts from IDEs. The simplest and the most universal way to achieve this is to create a `pom.xml` and then import it as a project. For this purpose Idea CE could be auto-downloaded and installed.

Will this `pom.xml` contain a single dependency to Bear?

IDE will be used for code completion and syntax highlighting. And Bear will be used for running.

### Run commands from console

    bear$ $.useShell(Remote)

    bearsh$ use hosts ...
    bearsh@hosts$ pwd && ls
    bearsh@hosts$ sudo mkdir test
    bearsh@hosts$ $.sys.line().sudo().raw("mkdir test").run()

#### Run a command for multiple hosts

    bearsh$ use stage stage1
    bearsh@stage1$ pwd && ls

#### Running scripts

- It must be easy to translate console into scripts
- Shell mode for plugins. I.e.

```ruby
plugin.runShellScript <<-HERE
    select 1 from dual;
    show tables;
HERE
```

And implementation will use this method.

### Utilize Maven

[Link back to H2](#id-goes-here)

### Write a new plugin

### maven-installation-plugin vs the Bear

## Implementation details

### To improve

- Settings should be imported into the script. There could be several settings possible and it's a good practise to use language features.
- Script must initialize the settings and plugins implicitly or not.

### Abstract Console

In a common-case scenario you send text to console and you get the response. If you send commands quickly, they are run in parallel in separate session.

```java
class AbstractConsole {
    void sendCommand(callback(text) -> ..., int timeoutMs);
}
```

```java

class CompositeConsole {
    List<AbstractConsole> consoles;

    void sendCommand(callback(text) -> ..., int timeoutMs){
        send to each
        join threads and wait for the response, update data on arrival
    }
}


class CompositeArrival {
    List<Entry> entries;

    class Entry{
        DynVar<String> text;
        ConsoleResult result; // text + result
    }

    // this method will find major differences if there are between several text outputs
    // so the representational layer could display them
    void findDifferences(float threshold){
    }
}

```

### Console terminals

http://terminal.jcubic.pl/examples.php, https://duckduckgo.com/tty/, https://www.docker.io/gettingstarted/#4


## Bear? WTF?

- The project started in Russia and Russia is famous for two things - vodka and bears drinking vodka
- Name Vodka is nice, but does not remind me of super-powers
- Saying "install it with a bear" or "bear is my weapon of choice" sounds at least less awkward then with vodka
