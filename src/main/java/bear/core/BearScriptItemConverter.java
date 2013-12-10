package bear.core;

import bear.main.event.TextConsoleEventToUI;
import bear.main.phaser.OnceEnteredCallable;
import bear.plugins.Plugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.task.*;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import groovy.lang.GroovyShell;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

/**
 * Scope: SESSION.
 */
class BearScriptItemConverter {
    private static final Logger logger = LoggerFactory.getLogger(BearScriptItemConverter.class);

    private final GlobalContext global;
    List<BearParserScriptSupplier.ScriptError> errors = new ArrayList<BearParserScriptSupplier.ScriptError>();

    public BearScriptItemConverter(GlobalContext global) {
        this.global = global;
    }

    public static Task<TaskDef> wrapIntoGlobalTask(final SessionContext $, final Task parent, final Task<?> task) {

        return new Task<TaskDef>(new TaskContext<TaskDef>(null, parent, $), new TaskCallable() {
            final OnceEnteredCallable<TaskResult> onceEnteredCallable = new OnceEnteredCallable<TaskResult>();

            @Override
            public TaskResult call(final SessionContext $, Task taskContext, Object input) throws Exception{
                return onceEnteredCallable.runOnce(new Callable<TaskResult>() {
                    @Override
                    public TaskResult call() throws Exception {
                        return $.runner.runSession(task);
                    }
                }).get();
            }
        });
    }

    /**
     * Scope: SESSION
     *
     *
     * @param scriptItem
     * @return
     */
    public TaskDef<Task> convertItemToTask(final ScriptItem scriptItem) {

        final List<String> executableLines = new ArrayList<String>(scriptItem.lines.size());
        final List<String> directivesLines = new ArrayList<String>();

        final List<String> lines = scriptItem.lines;

        for (String line : lines) {
            if (line.startsWith(":")) {
                directivesLines.add(line);
            } else {
                executableLines.add(line);
            }
        }

        scriptItem.assignVariables(global);

        if (!executableLines.isEmpty()) {
            return new TaskDef<Task>(scriptItem.getScriptName(), new SingleTaskSupplier<Task>() {
                @Override
                public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
                    scriptItem.assignVariables($);

                    final Plugin currentPlugin = getPlugin(scriptItem.pluginName);

                    for (int i = 0; i < directivesLines.size(); i++) {
                        String line = directivesLines.get(i);
                        String firstWord = StringUtils.substringBetween(line, ":", " ");

                        errors.add(new BearParserScriptSupplier.ScriptError(
                            line,
                            scriptItem.startsAtIndex + i, "unknown command: " + firstWord));
                    }

                    final Task<?> task;
                    if (currentPlugin.getShell().multiLine()) {
//                            shellContext.name = executableLines.get(0);
                        String script = Joiner.on("\n").join(executableLines);

                        task = currentPlugin.getShell().interpret(script, $, parent, null);

                    }else{
                        throw new UnsupportedOperationException("todo copy from an old version");
                    }

                    if(scriptItem.global){
                        return wrapIntoGlobalTask($, parent, task);
                    }

                    return task;
                }
            });
        } else {
            return TaskDef.EMPTY;
        }
    }


    private BearScriptRunner.MessageResponse setVariable(String line, SessionContext $) {
        String command = substringAfter(line, " ").trim();

        String varName = substringBefore(command, "=");
        String expression = substringAfter(command, "=");

        GroovyShell shell = new GroovyShell(global.getPlugin(GroovyShellPlugin.class).getShell().getLocalBinding());

        logger.info("evaluating: '{}'...", expression);

        Object o = shell.evaluate(expression);

        $.putConst(varName, o);

        return new BearScriptRunner.MessageResponse(String.format("assigned '%s' to '%s'", varName, o));
    }

    private Plugin getPlugin(final String pluginName) {
        Preconditions.checkNotNull(pluginName, "plugin name is null");

        List<Class<? extends Plugin>> matchingClasses = newArrayList(Collections2.filter(getPlugins(),
            new Predicate<Class<? extends Plugin>>() {
                @Override
                public boolean apply(Class<? extends Plugin> input) {
                    Shell shell = input.getAnnotation(Shell.class);
                    if (shell != null && shell.value().equalsIgnoreCase(pluginName)) return true;
                    return input.getSimpleName().toLowerCase().contains(pluginName);
                }
            }));

        if (matchingClasses.isEmpty()) {
            BearParserScriptSupplier.ui.warn(new TextConsoleEventToUI("shell", "no plugins found for '<i>" + pluginName + "</i>'\n"));
            throw new RuntimeException("no plugins found for '" + pluginName + "'");
        }

        if (matchingClasses.size() > 1) {
            BearParserScriptSupplier.ui.warn(new TextConsoleEventToUI("shell", "1+ plugins found for '<i>" + pluginName + "</i>': " + matchingClasses + "\n"));
            throw new RuntimeException("1+ plugins found for '" + pluginName + "': " + pluginName);
        }

        return global.getPlugin(matchingClasses.get(0));
    }

    private List<Class<? extends Plugin>> pluginList;

    private List<Class<? extends Plugin>> getPlugins() {
        if (pluginList == null) {
            pluginList = new ArrayList<Class<? extends Plugin>>(new Reflections("bear.plugin")
                .getSubTypesOf(Plugin.class));
        }

        return pluginList;
    }

}
