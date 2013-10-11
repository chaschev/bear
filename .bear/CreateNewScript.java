import bear.core.Bear;
import bear.core.SessionContext;
import bear.core.Stage;
import bear.cli.CommandLine;
import bear.main.Script;
import bear.vcs.SvnCLIPlugin;
import bear.vcs.VcsCLIPlugin;
import bear.session.Question;
import bear.session.SystemEnvironment;
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
    public void configure() throws Exception {
        final Bear bear = global.bear;

        new Question("step 1, choose the stage: ",
            transform(global.localCtx().var(bear.stages).getStages(), new Function<Stage, String>() {
                public String apply(Stage s) {
                    return s.name;
                }
            }),
            bear.stage
        ).ask();

        final Stage stage = global.localCtx.var(bear.getStage);
        final SystemEnvironment remoteEnv = stage.findRemoteEnvironment();

        final SessionContext $ = remoteEnv.$();

        List<String> branches = Lists.newArrayList("trunk/");

        branches.addAll(remoteVcsLs(bear, remoteEnv, $, "branches"));
        branches.addAll(remoteVcsLs(bear, remoteEnv, $, "tags"));

        new Question("step 2, choose a branch: ",
            branches,
            bear.vcsBranchName
        ).ask();

        System.out.println("skipping step 3 ('define common options: interactive, dry')");

        new Question("step 3, choose a task: ",
            Lists.newArrayList("deploy", "restartApp"),
            bear.taskName
        ).ask();

        System.out.printf(
            "please, review your conf: %n" +
                "stage: %s%n" +
                "branch: %s%n" +
                "task: %s%n",
            global.var(bear.stage),
            global.var(bear.vcsBranchName),
            global.var(bear.taskName)
        );

        global.console().ask("enter a script name to save to: ", bear.tempUserInput, null);

        final String scriptName = global.var(bear.tempUserInput) + "Script";

        final File newScriptFile = new File(scriptsDir, scriptName + ".java");
        FileUtils.writeStringToFile(
            newScriptFile,
            "import bear.core.Script;\n" +
                "\n" +
                "public class " + scriptName + " extends Script{\n" +
                "\t@Override\n" +
                "\tpublic void run() throws Exception {\n" +
                "\t\tbear.stage.defaultTo(\"" + $.var(bear.stage) + "\");\n" +
                "\t\tbear.vcsBranchName.defaultTo(\"" + $.var(bear.vcsBranchName) + "\");\n" +
                "\t\tbear.task.defaultTo(\"" + $.var(bear.taskName) + "\");\n" +
                "\n" +
                "\t\tglobal.run();\n" +
                "\t\tglobal.shutdown();\n" +
                "\t}\n" +
                "}\n     "
        );

        System.out.printf("your script has been saved to %s%n", newScriptFile.getAbsolutePath());
        System.out.printf("you may now restart bear to run it%n");
    }

    private static List<String> remoteVcsLs(Bear bear, SystemEnvironment remoteEnv, SessionContext $, final String dir) {
        final VcsCLIPlugin.Session vcsCLI = $.var(bear.vcs);

        final CommandLine<SvnCLIPlugin.LsResult> line = vcsCLI.ls($.joinPath(bear.repositoryURI, dir));

        line.timeoutMs(20000);

        final SvnCLIPlugin.LsResult result = remoteEnv.sendCommand(line, vcsCLI.passwordCallback());

        return Lists.transform(result.getFiles(), new Function<String, String>() {
            public String apply(String input) {
                return dir + "/" + input;
            }
        });
    }
}
