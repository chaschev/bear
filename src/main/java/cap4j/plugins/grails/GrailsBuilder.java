package cap4j.plugins.grails;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.java.JavaPlugin;
import cap4j.scm.CommandLine;
import cap4j.scm.CommandLineResult;
import cap4j.scm.VcsCLI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:25 PM
*/
public class GrailsBuilder {
    private static final Logger logger = LoggerFactory.getLogger(GrailsBuilder.class);

    SessionContext ctx;

    GrailsPlugin grails;
    JavaPlugin java;
    CapConstants cap;

    public GrailsBuilder(SessionContext ctx, GlobalContext global) {
        this.ctx = ctx;
        grails = global.getPlugin(GrailsPlugin.class);
        java = global.getPlugin(JavaPlugin.class);
        cap = global.cap;
    }

    public GrailsBuildResult build() {
        logger.info("building Grails WAR...");

        System.out.println(ctx.var(cap.realRevision));

        final String grailsExecPath = ctx.var(grails.grailsExecPath);

        String projectPath = ctx.var(grails.projectPath);

        final VcsCLI.Script script = new VcsCLI.Script()
            .cd(projectPath);

        if (ctx.varB(grails.clean)) {
            script
                .add(newGrailsCommand(grailsExecPath).a("clean"));
        }

        final String warName = ctx.var(grails.releaseWarPath);

        script.add(
            newGrailsCommand(grailsExecPath).a(
                "war",
                warName));

        final CommandLineResult clResult = ctx.system.run(script);

        return new GrailsBuildResult(clResult.result, ctx.joinPath(projectPath, warName));
    }

    private CommandLine newGrailsCommand(String grailsExecPath) {
        return ctx.newCommandLine()
            .setVar("JAVA_HOME", ctx.var(java.homePath))
            .a(grailsExecPath)
            .timeoutMs(600000);
    }
}
