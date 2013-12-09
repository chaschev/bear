package bear.core;

import bear.main.BearRunner2;
import bear.main.Response;
import bear.main.event.RMIEventToUI;
import bear.plugins.Plugin;
import bear.task.Task;
import bear.task.TaskDef;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearScriptRunner {
    static final Logger logger = LoggerFactory.getLogger(BearScriptRunner.class);
    static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    GlobalContext global;
    Bear bear;

    final Plugin initialPlugin;
    final IBearSettings settings;

    private GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearScriptRunner(GlobalContext global, Plugin currentPlugin, IBearSettings settings) {
        this.global = global;
        this.initialPlugin = currentPlugin;
        this.settings = settings;
        this.bear = global.bear;
    }

    /**
     * @param scriptSupplier A supplier for a script, i.e. a parser or a single item (at the moment a groovy script).
     */
    public RunResponse exec(Supplier<BearScript2.BearScriptParseResult> scriptSupplier, boolean interactive) {

        final BearScript2.BearScriptParseResult parseResult = scriptSupplier.get();

        if (!parseResult.globalErrors.isEmpty()) {
            return new RunResponse(parseResult.globalErrors);
        }

        final List<ScriptItem> scriptItems = parseResult.scriptItems;

        final BearScriptItemConverter scriptExecContext = new BearScriptItemConverter(global);

        List<TaskDef<Task>> taskList = newArrayList(transform(scriptItems, new Function<ScriptItem, TaskDef<Task>>() {
            @Nullable
            @Override
            public TaskDef<Task> apply(ScriptItem scriptItem) {
                return scriptExecContext.convertItemToTask(scriptItem);
            }
        }));

        return exec(taskList, interactive);
    }

    public RunResponse exec(List<TaskDef<Task>> taskList, boolean interactive) {
        if (interactive) {
            //this disable dependencies checks, verifications and installations
            global.putConst(bear.internalInteractiveRun, true);
        }

        PreparationResult preparationResult = new BearRunner2(settings, factory).createRunContext();

        List<SessionContext> $s = preparationResult.getSessions();

        GridBuilder gridBuilder = new GridBuilder().addAll(taskList);

        GlobalTaskRunner globalTaskRunner = new GlobalTaskRunner(global, gridBuilder.build(), preparationResult);

        gridBuilder.injectGlobalRunner(globalTaskRunner);

        ui.info(new RMIEventToUI("terminals", "onScriptStart", getHosts($s)));

        globalTaskRunner.startParties(global.localExecutor);

        return new RunResponse(globalTaskRunner, getHosts(preparationResult.getSessions()));
    }

    public static List<RunResponse.Host> getHosts(List<SessionContext> $s) {
        return transform($s, new Function<SessionContext, RunResponse.Host>() {
            public RunResponse.Host apply(SessionContext $) {
                return new RunResponse.Host($.sys.getName(), $.sys.getAddress());
            }
        });
    }

    public static class RunResponse extends Response {
        public List<BearScript2.ScriptError> errors;
        GlobalTaskRunner globalRunner;

        public RunResponse(List<BearScript2.ScriptError> errors) {
            this.errors = errors;
        }

        public static class Host {
            public String name;
            public String address;

            public Host(String name, String address) {
                this.name = name;
                this.address = address;
            }
        }

        public List<Host> hosts;

        public RunResponse(GlobalTaskRunner globalTaskRunner, List<Host> hosts) {
            this.globalRunner = globalTaskRunner;
            this.hosts = hosts;
        }

        public GlobalTaskRunner getGlobalRunner() {
            return globalRunner;
        }
    }

    public static class MessageResponse extends Response {
        public String message;

        public MessageResponse(String message) {
            this.message = message;
        }
    }

    public static class UIContext {
        public String settingsName;
        public String script;
        public String shell;
    }

    public static class SwitchResponse extends MessageResponse {
        public final String pluginName;
        public final String prompt;

        public SwitchResponse(String pluginName, String prompt) {
            super("switched to shell '<i>" + pluginName + "</i>'");
            this.pluginName = pluginName;
            this.prompt = prompt;
        }
    }

    static class ShellSessionContext {
        public final String sessionId = SessionContext.randomId();
        // seen as a task
        protected String phaseId;
    }
}
