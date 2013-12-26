package bear.plugins.misc;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.plugins.sh.WriteStringResult;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.util.Map;

/**
 * TODO: could be updated by using this example: https://github.com/yyuu/capistrano-upstart/
 * http://serverfault.com/questions/291546/centos-6-and-upstart
 * http://stackoverflow.com/questions/4335343/upstart-logging-output-enabled
 * example: https://gist.github.com/leon/2204773, exec start-stop-daemon --pidfile ${HOME}/RUNNING_PID --chuid $USER:$GROUP --exec ${HOME}/target/universal/stage/bin/${APP} --background --start -- -Dconfig.resource=$CONFIG -Dhttp.port=$PORT -Dhttp.address=$ADDRESS $EXTRA
 */

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class UpstartPlugin extends Plugin {

    // from http://ivarprudnikov.com/node-js-as-serivce-with-upstart-on-centos/
    public final DynamicVariable<String>
        startOn = Variables.newVar("started network"),
        stopOn = Variables.newVar("stopping network")
//        startOn = Variables.newVar("runlevel [2345]"),
//        stopOn = Variables.newVar("runlevel [016]")
            ;


    public UpstartPlugin(GlobalContext global) {
        super(global);

    }

    public final TaskDef<UpstartServices, TaskResult> create = new TaskDef<UpstartServices, TaskResult>(new SingleTaskSupplier<UpstartServices, TaskResult>() {
        @Override
        public Task<UpstartServices, TaskResult> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<UpstartServices, TaskResult> def) {
            return new Task<UpstartServices, TaskResult>(parent, new TaskCallable<UpstartServices, TaskResult>() {
                @Override
                public TaskResult call(SessionContext $, Task<UpstartServices, TaskResult> task) throws Exception {
                    UpstartServices services = task.getInput();
                    Preconditions.checkNotNull(services, "You need to specify upstart services.");

                    StringBuilder sb = new StringBuilder();

                    for (UpstartService service : services.services) {
                        sb.setLength(0);

                        for (Map.Entry<String, String> e : service.exportVars.entrySet()) {
                            sb.append("env ").append(e.getKey()).append("=").append(e.getValue()).append("\n");
                        }

                        for (Map.Entry<String, String> e : service.exportVars.entrySet()) {
                            sb.append("export ").append(e.getKey()).append("\n");
                        }

                        String text =
                            "" +
                                "#!upstart\n" +
                                "description \"" + service.description + "\"\n" +
                                "author      \"bear\"\n" +
                                field(service.dir, "chdir") +
                                "\n" +
                                "start on " + $.var(startOn) + "\n" +
                                "stop on " + $.var(stopOn) + "\n" +
                                "\n" +
                                "# exports\n" +
                                sb.toString() + "\n" +
                                "respawn\n" +
                                "respawn limit 5 60\n" +
                                "\n" +
                                "script\n" +
                                "    " + service.script + "\n" +
                                "end script\n" +
                                section(service.custom);

                        $.sys.writeString(text).toPath("/etc/init/" + service.name + ".conf").sudo().withPermissions("u+x,g+x,o+x").ifDiffers().run();
                    }

                    Optional<String> groupName = services.groupName;

                    if (groupName.isPresent()) {
                        for (String command : new String[]{"start", "stop", "status", "restart"}) {
                            String scriptName = groupName.get() + "-" + command;

                            String text = "";

                            for (UpstartService service : services.services) {
                                text += "sudo " + $.sys.getOsInfo().getHelper().upstartCommand(service.name, command) + "\n";
                            }

                            WriteStringResult r = $.sys.writeString(text)
                                .toPath("/usr/bin/" + scriptName)
                                .sudo()
                                .withPermissions("u+x,g+x,o+x")
                                .run();

                            if (!r.ok()) {
                                return r;
                            }
                        }
                    }

                    return TaskResult.OK;
                }
            });
        }
    });

    private static String field(Optional<String> field, String name) {
        return (field.isPresent() ? name +" " + field.get() + "\n" : "");
    }

    private static String section(Optional<String> field) {
        return (field.isPresent() ? field.get() + "\n" : "");
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
//        return new InstallationTaskDef<InstallationTask>() {
//            @Override
//            protected InstallationTask newSession(final SessionContext _$, Task parent) {
//                return new InstallationTask<InstallationTaskDef>(parent, this, _$) {
//                    {
//                        addDependency(new Dependency(toString(), _$).addCommands(
//                            "upstart"));
//                    }
//
//                    @Override
//                    public Dependency asInstalledDependency() {
//                        return Dependency.NONE;
//                    }
//                };
//            }
//        };
    }
}
