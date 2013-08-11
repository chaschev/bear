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

import static cap4j.core.CapConstants.*;
import static cap4j.core.CapConstants.stage;
import static cap4j.core.GlobalContext.localCtx;
import static com.google.common.collect.Lists.transform;

/**
 * User: achaschev
 * Date: 8/5/13
 */
public class CreateNewScript extends Script{

    @Override
    public void run() throws Exception {
        new Question("step 1, choose stage: ",
            transform(localCtx().var(CapConstants.stages).getStages(), new Function<Stage, String>() {
            public String apply(Stage s) {
                return s.name;
            }
        }),
            stage
        ).ask();

        final Stage stage = localCtx().var(CapConstants.getStage);
        final SystemEnvironment remoteEnv = stage.findRemoteEnvironment();

        final VarContext ctx = remoteEnv.ctx();

        List<String> branches = Lists.newArrayList("trunk/");

        branches.addAll(remoteVcsLs(remoteEnv, ctx, "branches"));
        branches.addAll(remoteVcsLs(remoteEnv, ctx, "tags"));

        new Question("step 2, choose branch: ",
            branches,
            vcsBranchName
        ).ask();

        System.out.println("skipping step 3 ('define common options: interactive, dry')");

        new Question("step 3, choose a task: ",
            Lists.newArrayList("deploy", "restartApp"),
            task
        ).ask();

        System.out.printf(
            "please, review your conf: %n" +
            "stage: %s%n" +
            "branch: %s%n" +
            "task: %s%n",
            localCtx().var(CapConstants.stage),
            localCtx().var(vcsBranchName),
            localCtx().var(task)
        );

        GlobalContext.console().ask("enter a script name to save to: ", tempUserInput, null);

        final String scriptName = localCtx().var(tempUserInput) + "Script";

        FileUtils.writeStringToFile(
            new File(scriptsDir, scriptName + ".java"),
            "import cap4j.core.*;\n" +
                "import cap4j.scm.CommandLine;\n" +
                "import cap4j.scm.SvnVcsCLI;\n" +
                "import cap4j.scm.VcsCLI;\n" +
                "import cap4j.session.DynamicVariable;\n" +
                "import cap4j.session.SystemEnvironment;\n" +
                "import com.chaschev.chutils.util.Exceptions;\n" +
                "import com.google.common.base.Function;\n" +
                "import com.google.common.collect.Lists;\n" +
                "import org.apache.commons.io.FileUtils;\n" +
                "\n" +
                "import java.io.*;\n" +
                "import java.util.List;\n" +
                "\n" +
                "import static cap4j.core.CapConstants.*;\n" +
                "import static cap4j.core.CapConstants.stage;\n" +
                "import static cap4j.core.GlobalContext.localCtx;\n" +
                "import static com.google.common.collect.Lists.transform;\n" +
                "\n" +
                "public class " + scriptName + " extends Script{\n" +
                "\t@Override\n" +
                "\tpublic void run() throws Exception {\n" +
                "\t\tstage.defaultTo(\"" + ctx.var(CapConstants.stage) + "\");\n" +
                "\t\tvcsBranchName.defaultTo(\"" + ctx.var(vcsBranchName) + "\");\n" +
                "\t\ttask.defaultTo(\"" + ctx.var(task) + "\");\n" +
                "\t}\n" +
                "}\n     "
        );
    }

    private static List<String> remoteVcsLs(SystemEnvironment remoteEnv, VarContext ctx, final String dir) {
        final VcsCLI vcsCLI = ctx.var(vcs);

        final CommandLine<SvnVcsCLI.LsResult> line = vcsCLI.ls(ctx.joinPath(repositoryURI, dir));

        line.timeoutMs(20000);

        final SvnVcsCLI.LsResult result = remoteEnv.run(line, vcsCLI.runCallback());

        return Lists.transform(result.getFiles(), new Function<String, String>() {
            public String apply( String input) {
                return dir + "/" + input;
            }
        });
    }
}
