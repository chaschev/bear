import cap4j.core.*;
import cap4j.scm.CommandLine;
import cap4j.scm.SvnVcsCLI;
import cap4j.scm.VcsCLI;
import cap4j.session.Question;
import cap4j.session.SystemEnvironment;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.List;

import static com.google.common.collect.Lists.transform;

/**
 * User: achaschev
 * Date: 8/5/13
 */
public class CreateNewScript extends Script{
    @Override
    public void run() throws Exception {
        final CapConstants cap = global.cap;

        new Question("step 1, choose stage: ",
            transform(global.localCtx().var(cap.stages).getStages(), new Function<Stage, String>() {
            public String apply(Stage s) {
                return s.name;
            }
        }),
            cap.stage
        ).ask();

        final Stage stage = global.localCtx.var(cap.getStage);
        final SystemEnvironment remoteEnv = stage.findRemoteEnvironment();

        final SessionContext ctx = remoteEnv.ctx();

        List<String> branches = Lists.newArrayList("trunk/");

        branches.addAll(remoteVcsLs(cap, remoteEnv, ctx, "branches"));
        branches.addAll(remoteVcsLs(cap, remoteEnv, ctx, "tags"));

        new Question("step 2, choose branch: ",
            branches,
            cap.vcsBranchName
        ).ask();

        System.out.println("skipping step 3 ('define common options: interactive, dry')");

        new Question("step 3, choose a task: ",
            Lists.newArrayList("deploy", "restartApp"),
            cap.task
        ).ask();

        System.out.printf(
            "please, review your conf: %n" +
                "stage: %s%n" +
                "branch: %s%n" +
                "task: %s%n",
            global.var(cap.stage),
            global.var(cap.vcsBranchName),
            global.var(cap.task)
        );

        global.console().ask("enter a script name to save to: ", cap.tempUserInput, null);

        final String scriptName = global.var(cap.tempUserInput) + "Script";

        FileUtils.writeStringToFile(
            new File(scriptsDir, scriptName + ".java"),
                "import cap4j.core.Script;\n" +
                "\n" +
                "public class " + scriptName + " extends Script{\n" +
                "\t@Override\n" +
                "\tpublic void run() throws Exception {\n" +
                "\t\tcap.stage.defaultTo(\"" + ctx.var(cap.stage) + "\");\n" +
                "\t\tcap.vcsBranchName.defaultTo(\"" + ctx.var(cap.vcsBranchName) + "\");\n" +
                "\t\tcap.task.defaultTo(\"" + ctx.var(cap.task) + "\");\n" +
                "\n" +
                "\t\tglobal.run();\n" +
                "\t\tglobal.shutdown();\n" +
                "\t}\n" +
                "}\n     "
        );
    }

    private static List<String> remoteVcsLs(CapConstants cap, SystemEnvironment remoteEnv, SessionContext ctx, final String dir) {
        final VcsCLI vcsCLI = ctx.var(cap.vcs);

        final CommandLine<SvnVcsCLI.LsResult> line = vcsCLI.ls(ctx.joinPath(cap.repositoryURI, dir));

        line.timeoutMs(20000);

        final SvnVcsCLI.LsResult result = remoteEnv.run(line, vcsCLI.runCallback());

        return Lists.transform(result.getFiles(), new Function<String, String>() {
            public String apply( String input) {
                return dir + "/" + input;
            }
        });
    }
}
