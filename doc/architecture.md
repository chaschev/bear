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

