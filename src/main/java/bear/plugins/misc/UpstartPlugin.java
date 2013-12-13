package bear.plugins.misc;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.plugins.sh.WriteStringInput;
import bear.plugins.sh.WriteStringResult;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.util.Map;

import static com.google.common.base.Optional.of;

/**
 * TODO: could be updated by using this example: https://github.com/yyuu/capistrano-upstart/
 *
 * http://stackoverflow.com/questions/4335343/upstart-logging-output-enabled
 * example: https://gist.github.com/leon/2204773, exec start-stop-daemon --pidfile ${HOME}/RUNNING_PID --chuid $USER:$GROUP --exec ${HOME}/target/universal/stage/bin/${APP} --background --start -- -Dconfig.resource=$CONFIG -Dhttp.port=$PORT -Dhttp.address=$ADDRESS $EXTRA
 */

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class UpstartPlugin extends Plugin {
    public final DynamicVariable<String>
        startOn = Variables.newVar("[2345]"),
        stopOn = Variables.newVar("[016]");


    public UpstartPlugin(GlobalContext global) {
        super(global);

    }

    public final TaskDef<Task> create = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task(parent, new TaskCallable<TaskDef>() {
                @Override
                public TaskResult call(SessionContext $, Task task, Object input) throws Exception {
                    UpstartServices upstartServices = (UpstartServices) input;
                    Preconditions.checkNotNull(upstartServices, "You need to specify upstart services.");

                    StringBuilder sb = new StringBuilder();

                    for (UpstartService service : upstartServices.services) {
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
                                field(service.user, "setuid") +
                                field(service.group, "setgid") +
                                "\n" +
                                "start on runlevel " + $.var(startOn) + "\n" +
                                "stop on runlevel " + $.var(stopOn) + "\n" +
                                "\n" +
                                "# exports\n" +
                                sb.toString() + "\n" +
                                "respawn\n" +
                                "respawn limit 5 60\n" +
                                "\n" +
                                "script\n" +
                                "    " + service.script + "\n" +
                                "end script";

                        return $.sys.writeString(WriteStringInput.str("/etc/init/" + service.name + ".conf", text).sudo().withPermissions("u+x,g+x,o+x").ifDiffers());
                    }

                    Optional<String> groupName = upstartServices.groupName;

                    if(groupName.isPresent()){
                        for(String command : new String[]{"start", "stop", "status", "restart"}){
                            String scriptName = groupName.get() + "_" + command;

                            String text = "";

                            for (UpstartService service : upstartServices.services) {
                                text += "sudo " + $.sys.getOsInfo().getHelper().serviceCommand(service.name, command);
//                                text += "sudo service " + service.name + " " + command + "\n";
                            }

                            WriteStringResult r = $.sys.writeString(new WriteStringInput("/usr/bin/" + scriptName, text, true, Optional.<String>absent(), of("u+x,g+x,o+x")));

                            if(!r.ok()){
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
