package cap4j.plugins.tomcat;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;
import cap4j.session.Result;
import cap4j.session.VariableUtils;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import cap4j.task.Tasks;
import com.google.common.base.Function;
import org.apache.commons.io.FilenameUtils;

import static cap4j.core.CapConstants.dynamic;
import static cap4j.core.CapConstants.dynamicNotSet;
import static cap4j.core.CapConstants.strVar;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:24 PM
*/
public class TomcatPlugin extends Plugin {
    Tasks tasks;

    public TomcatPlugin(GlobalContext global) {
        super(global);
        this.tasks = global.tasks;
    }

    public void init(){
        tasks.restartApp.addBeforeTask(new Task("restart tomcat") {
            @Override
            protected TaskResult run(TaskRunner runner) {
                system.sudo().rm(ctx.var(warCacheDirs));
                system.sudo().run(ctx.newCommandLine()
                    .a("service", "tomcat6", "stop")
                    .semicolon()
                    .sudo()
                    .a("service", "tomcat6", "start")
                    .timeoutMs(60000)
                );

                return new TaskResult(Result.OK);
            }
        });
    }

    public final DynamicVariable<String>
        webappsUnix = strVar("webappsWin", "/var/lib/tomcat6/webapps").defaultTo("/var/lib/tomcat6/webapps"),
        webappsWin = dynamicNotSet("webappsWin", ""),
        webapps = strVar("tomcatHome", "").setDynamic(new Function<SessionContext, String>() {
            public String apply(SessionContext ctx) {
                return ctx.system.isUnix() ? ctx.var(webappsUnix) : ctx.var(webappsWin);
            }
        }),
        warName = strVar("warName", "i.e. ROOT.war"),
        warPath = VariableUtils.joinPath("warPath", webapps, warName)
            ;

    public final DynamicVariable<String[]> warCacheDirs = dynamic("warCacheDirs", "", new Function<SessionContext, String[]>() {
        public String[] apply(SessionContext ctx) {
            final String name = FilenameUtils.getBaseName(ctx.var(warName));
            return new String[]{
                ctx.system.joinPath(ctx.var(webapps), name)
            };
        }
    });
}
