package cap4j.plugins.tomcat;

import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.ZippedToolPlugin;
import cap4j.scm.CommandLineResult;
import cap4j.session.SystemEnvironment;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import org.apache.commons.lang3.StringUtils;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:24 PM
*/
public class MavenPlugin extends ZippedToolPlugin {

    public MavenPlugin(GlobalContext global) {
        super(global);

        version.defaultTo("3.0.5", true);
        toolname.defaultTo("maven", true);
        toolDistrName.defaultTo("apache-maven", true);
        distrFilename.setDynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return concat(versionName, "-bin.tar.gz");
            }
        });

        distrWwwAddress.setDynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return concat("http://apache-mirror.rbc.ru/pub/apache/maven/maven-3/", version,
                    "/binaries/apache-maven-", version, "-bin.tar.gz");
            }
        });
    }

    public void init(){

    }

    public final Task setup = new ZippedToolTask("setup maven") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            clean();

            download();

            extractToHomeDir();

            shortCut("mvn", "mvn");

            final CommandLineResult r = system.run(extractToHomeScript,
                SystemEnvironment.passwordCallback($.var(cap.sshPassword))
            );

            verifyExecution();

            return new TaskResult(r);
        }

        @Override
        protected String extractVersion(String output) {
            return StringUtils.substringBetween(
                output,
                "Apache Maven ", " ");
        }

        @Override
        protected String createVersionCommandLine() {
            return "mvn --version";
        }
    };


    @Override
    public Task getSetup() {
        return setup;
    }
}
