package cap4j.plugins.tomcat;

import cap4j.core.VarContext;
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
public class TomcatPlugin {
    public static void init(){
        Tasks.restartApp.addBeforeTask(new Task("restart tomcat") {
            @Override
            protected TaskResult run(TaskRunner runner) {
                system.sudo().rm(ctx.var(tomcatWarCacheDirs));
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

    public static final DynamicVariable<String>
        tomcatWebappsUnix = strVar("tomcatWebappsWin", "/var/lib/tomcat6/webapps").defaultTo("/var/lib/tomcat6/webapps"),
        tomcatWebappsWin = dynamicNotSet("tomcatWebappsWin", ""),
        tomcatWebapps = strVar("tomcatHome", "").setDynamic(new Function<VarContext, String>() {
            public String apply(VarContext ctx) {
                return ctx.system.isUnix() ? ctx.var(tomcatWebappsUnix) : ctx.var(tomcatWebappsWin);
            }
        }),
        tomcatWarName = strVar("tomcatWarName", "i.e. ROOT.war"),
        tomcatWarPath = VariableUtils.joinPath("tomcatWarPath", tomcatWebapps, tomcatWarName)
            ;

    public static final DynamicVariable<String[]> tomcatWarCacheDirs = dynamic("tomcatWarCacheDirs", "", new Function<VarContext, String[]>() {
        public String[] apply(VarContext ctx) {
            final String name = FilenameUtils.getBaseName(ctx.var(tomcatWarName));
            return new String[]{
                ctx.system.joinPath(ctx.var(tomcatWebapps), name)
            };
        }
    });

}
