package cap4j.plugins.grails;

import cap4j.CapConstants;
import cap4j.VarContext;
import cap4j.plugins.java.JavaPlugin;
import cap4j.scm.CommandLine;
import cap4j.scm.CommandLineResult;
import cap4j.scm.Vcs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:25 PM
*/
public class GrailsBuilder {
    private static final Logger logger = LoggerFactory.getLogger(GrailsBuilder.class);

    VarContext ctx;


    public GrailsBuilder(VarContext ctx) {
        this.ctx = ctx;
    }

    public GrailsBuildResult build() {
        logger.info("building Grails WAR...");

        System.out.println(ctx.var(CapConstants.realRevision));
        System.out.println(ctx.var(CapConstants.realRevision));

        final String grailsExecPath = ctx.var(GrailsPlugin.grailsExecPath);

        String projectPath = ctx.var(GrailsPlugin.projectPath);

        final Vcs.Script script = new Vcs.Script()
            .cd(projectPath);

        if (ctx.varB(GrailsPlugin.grailsClean)) {
            script
                .add(newGrailsCommand(grailsExecPath).a("clean"));
        }

        final String warName = ctx.var(GrailsPlugin.releaseWarPath);

        script.add(
            newGrailsCommand(grailsExecPath).a(
                "war",
                warName));

        final CommandLineResult clResult = ctx.system.run(script);

        return new GrailsBuildResult(clResult.result, ctx.joinPath(projectPath, warName));
    }

    private CommandLine newGrailsCommand(String grailsExecPath) {
        return ctx.newCommandLine()
            .setVar("JAVA_HOME", ctx.var(JavaPlugin.javaHomePath))
            .a(grailsExecPath)
            .timeoutMs(600000);
    }
}
