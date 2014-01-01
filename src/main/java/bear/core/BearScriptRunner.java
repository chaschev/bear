package bear.core;

import bear.main.BearRunner2;
import bear.main.Response;
import bear.main.event.RMIEventToUI;
import bear.main.phaser.Phase;
import bear.plugins.Plugin;
import bear.task.TaskDef;
import bear.task.TaskResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

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
    final BearProject settings;

    private GlobalContextFactory factory = GlobalContextFactory.INSTANCE;
    private Map<Object, Object> variables;

    public BearScriptRunner(GlobalContext global, Plugin currentPlugin, BearProject settings) {
        this.global = global;
        this.initialPlugin = currentPlugin;
        this.settings = settings;
        this.bear = global.bear;
    }

    /**
     * @param scriptSupplier A supplier for a script, i.e. a parser or a single item (at the moment a groovy script).
     *
     */

    //todo remove, not used
    public RunResponse exec(Supplier<BearParserScriptSupplier.BearScriptParseResult> scriptSupplier, boolean interactive) {

        final BearParserScriptSupplier.BearScriptParseResult parseResult = scriptSupplier.get();

        if (!parseResult.globalErrors.isEmpty()) {
            return new RunResponse(parseResult.globalErrors);
        }

        final List<ScriptItem> scriptItems = parseResult.scriptItems;

        final BearScriptItemConverter scriptExecContext = new BearScriptItemConverter(global);

        List<TaskDef<Object, TaskResult>> taskList = newArrayList(transform(scriptItems, new Function<ScriptItem, TaskDef<Object, TaskResult>>() {
            @Nullable
            @Override
            public TaskDef<Object, TaskResult> apply(ScriptItem scriptItem) {
                return scriptExecContext.convertItemToTask(scriptItem);
            }
        }));

        return exec(taskList, interactive);
    }

    public RunResponse exec(List<TaskDef<Object, TaskResult>> taskList, boolean interactive) {
        GridBuilder gridBuilder = new GridBuilder().addAll(taskList);

        return exec(gridBuilder, interactive);
    }

    public RunResponse exec(GridBuilder gridBuilder, boolean interactive) {
        List<Phase<TaskResult,BearScriptPhase<Object, TaskResult>>> phases = gridBuilder.build();


        if (interactive) {
            //this disable dependencies checks, verifications and installations
            global.putConst(bear.internalInteractiveRun, true);
        }

        Map<Object, Object> savedVariables = null;
        if(variables != null){
            savedVariables = global.putMap(variables);
        }

        PreparationResult preparationResult = new BearRunner2(settings, factory).createRunContext();

        List<SessionContext> $s = preparationResult.getSessions();


        GlobalTaskRunner globalTaskRunner = new GlobalTaskRunner(global, phases, preparationResult);

        gridBuilder.injectGlobalRunner(globalTaskRunner);

        ui.info(new RMIEventToUI("terminals", "onScriptStart", getHosts($s)));

        globalTaskRunner.startParties(global.sessionsExecutor);

        RunResponse runResponse = new RunResponse(globalTaskRunner, getHosts(preparationResult.getSessions()));

        runResponse.savedVariables = savedVariables;

        return runResponse;
    }

    public static List<RunResponse.Host> getHosts(List<SessionContext> $s) {
        return transform($s, new Function<SessionContext, RunResponse.Host>() {
            public RunResponse.Host apply(SessionContext $) {
                return new RunResponse.Host($.sys.getName(), $.sys.getAddress());
            }
        });
    }

    public BearScriptRunner withVars(Map<Object, Object> variables) {
        this.variables = variables;
        return this;
    }

    public static class RunResponse extends Response {
        public List<BearParserScriptSupplier.ScriptError> errors;
        GlobalTaskRunner globalRunner;
        private Map<Object, Object> savedVariables;

        public RunResponse(List<BearParserScriptSupplier.ScriptError> errors) {
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

        @JsonIgnore
        public GlobalTaskRunner getGlobalRunner() {
            return globalRunner;
        }

        @JsonIgnore
        public Map<Object, Object> getSavedVariables() {
            return savedVariables;
        }
    }

    public static class MessageResponse extends Response {
        public String message;

        public MessageResponse(String message) {
            this.message = message;
        }
    }

    public static class UIContext {
        public String projectPath;
        public String shell;
        public String projectMethodName;
        public String plugin;
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
