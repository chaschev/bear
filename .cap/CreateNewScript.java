import cap4j.core.*;
import cap4j.cli.CommandLine;
import cap4j.scm.SvnCLI;
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
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class CreateNewScript extends Script {
    @Override
    public void run() throws Exception {
        final Cap cap = global.cap;

        new Question("step 1, choose the stage: ",
            transform(global.localCtx().var(cap.stages).getStages(), new Function<Stage, String>() {
                public String apply(Stage s) {
                    return s.name;
                }
            }),
            cap.stage
        ).ask();

        final Stage stage = global.localCtx.var(cap.getStage);
        final SystemEnvironment remoteEnv = stage.findRemoteEnvironment();

        final SessionContext $ = remoteEnv.ctx();

        List<String> branches = Lists.newArrayList("trunk/");

        branches.addAll(remoteVcsLs(cap, remoteEnv, $, "branches"));
        branches.addAll(remoteVcsLs(cap, remoteEnv, $, "tags"));

        new Question("step 2, choose a branch: ",
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

        final File newScriptFile = new File(scriptsDir, scriptName + ".java");
        FileUtils.writeStringToFile(
            newScriptFile,
            "import cap4j.core.Script;\n" +
                "\n" +
                "public class " + scriptName + " extends Script{\n" +
                "\t@Override\n" +
                "\tpublic void run() throws Exception {\n" +
                "\t\tcap.stage.defaultTo(\"" + $.var(cap.stage) + "\");\n" +
                "\t\tcap.vcsBranchName.defaultTo(\"" + $.var(cap.vcsBranchName) + "\");\n" +
                "\t\tcap.task.defaultTo(\"" + $.var(cap.task) + "\");\n" +
                "\n" +
                "\t\tglobal.run();\n" +
                "\t\tglobal.shutdown();\n" +
                "\t}\n" +
                "}\n     "
        );

        System.out.printf("your script has been saved to %s%n", newScriptFile.getAbsolutePath());
        System.out.printf("you may now restart cap4j to run it%n");
    }

    private static List<String> remoteVcsLs(Cap cap, SystemEnvironment remoteEnv, SessionContext $, final String dir) {
        final VcsCLI vcsCLI = $.var(cap.vcs);

        final CommandLine<SvnCLI.LsResult> line = vcsCLI.ls($.joinPath(cap.repositoryURI, dir));

        line.timeoutMs(20000);

        final SvnCLI.LsResult result = remoteEnv.run(line, vcsCLI.passwordCallback());

        return Lists.transform(result.getFiles(), new Function<String, String>() {
            public String apply(String input) {
                return dir + "/" + input;
            }
        });
    }
}
