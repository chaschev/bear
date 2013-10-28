### Plugin

- have dependencies
- plugin deps: another plugin, system dep (session scope)
- may have configuration (i.e. VCS credentials, SSH address)
- can be dep injected (i.e. each Plugin session uses SSH plugin session for this host by: invoking newSession on the injected SSHTaskDef)
- can be a transitive dependency (configurable dep checks for transitive deps)

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

### DI

Think of a DI engine

### Distributed bear installation

This is an idea so far.

Installation:

1. Copy files
2. Install as a service

Remote execution plugin

- Netty/Akka/KryoNet request handler (so there is BearFX, BearCLI, BearCLI + BearRemotePlugin)
- Receives messages: { settings: {type: [file|string], }, shell:
