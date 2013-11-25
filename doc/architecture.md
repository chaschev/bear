### Plugin

Plugin is normally a container, or an "organizational unit" - a collection of tasks and dependencies.

- has dependencies
- can be injected
- his vars are named properly
- plugin deps: another plugin, system dep (session scope)
- can be a dependency (configurable dep checks for transitive deps)

### Task & TaskDef

TaskDef is a factory of Tasks. Task is a session to run on a host. It is associated with a `SessionContext $`.

TaskDef normally, but not necessary corresponds to a row in a ComputingGrid. (There can be a case when TaskDef is used to create a single Task).

Task is 99% cases created in a TaskDef and corresponds to a cell of a ComputingGrid. Task can be a shell command, a shell script, a groovy script, installation task, deployment task, etc.

Plugin initialization:

0. Init variables

---

0. Add implicit dependency plugins
1. Build dep graph
2. Create plugins
3. Dependency inject plugins
2. Error if there are cycles
5. Topologically sort the graph
6. Initialize plugins (plugin.initPlugin)

### Bear Script execution

- global context is reused
- settings are created each time outside the script
- settings can be empty, you can move it's code to the snippet

Script is parsed and split into `ScriptItem`s. Each `ScriptItem` corresponds to an execution phase.

When a script reaches a phase with OK result, a timeout job is created with a value of it's duration * ~3 waiting for others. When it's hit or when all are arrived results are divided into groups based on levenstein metric.

#### Motivation

* Some machines might just fail/hang up, so we *can't synchronize phases*.
* Why not have a single phase (i.e. a groovy script)? Because in script you would want to segregate phases - i.e. diff sql result against sql, possibly synchronize at this point.

### DI

Think of a DI engine

### Distributed bear installation

This is an idea so far.

Installation:

1. Copy files
2. Install as a service

Remote execution plugin

- Netty/Akka/KryoNet request handler (so there is BearFX, BearCLI, BearCLI + BearRemotePlugin)
- Receiving messages:
```javascript
{
    settings:
        {type: '[file|string]', text:'...'},
    shell: 'mongo',
    scriptText: 'plugin(MongoPlugin.class).blabla OR new MongoDriver'
}
```

### AngularJS

- your app needs to start initialized (to be similar to a server side interaction)

### Refactor Todo

- VarRegistry, Cli -> VarsFramework
- Separate BearFXCli, ConsoleCli and VarsCli
- Properties initialization (for a session vs globally)
- Global creation/reusing in a new classloader (reusing)
-

### Hosts configuration

- Stages is a self-contained configuration of possible hosts, stages and roles.
- Role is tag for quick referencing.
- Effective configuration is a set of hosts.
