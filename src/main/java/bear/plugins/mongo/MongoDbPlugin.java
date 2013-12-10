package bear.plugins.mongo;

import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.Shell;
import bear.plugins.Plugin;
import bear.plugins.sh.SystemSession;
import bear.plugins.sh.UnixSubFlavour;
import bear.plugins.sh.WriteStringInput;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.Versions;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bear.plugins.sh.RmInput.newRm;
import static bear.plugins.sh.SystemEnvironmentPlugin.sshPassword;
import static bear.session.Variables.*;
import static bear.session.Versions.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Shell("mongo")
public class MongoDbPlugin extends Plugin {
    public final DynamicVariable<String>
        version = undefined(),
        dbHost = newVar("localhost").desc("database host"),
        dbPort = newVar("27017").desc("database port"),
        dbName = strVar().desc("database name"),
        connectionString = concat(dbHost, ":", dbPort, "/", dbName),
        serverPackage = newVar("mongo-10gen-server"),
        clientPackage = newVar("mongo-10gen");

    public final DynamicVariable<VersionConstraint> versionConstraint = condition(isSet(version), convert(version, new Function<String, VersionConstraint>() {
        public VersionConstraint apply(String input) {
            return newVersionConstraint(input);
        }
    }), newVar(ANY));

    public final DynamicVariable<? extends Map<UnixSubFlavour, String>> repo = newVar(
        new ImmutableMap.Builder<UnixSubFlavour, String>()
            .put(UnixSubFlavour.CENTOS, "http://downloads-distro.mongodb.org/repo/redhat/os/x86_64/")
            .build()
    );

    public MongoDbPlugin(GlobalContext global) {
        super(global);

        shell = new MongoDbShellMode(MongoDbPlugin.this);
    }

    public final InstallationTaskDef<InstallationTask> setup = new InstallationTaskDef<InstallationTask>(new SingleTaskSupplier() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new InstallationTask<InstallationTaskDef>(parent, setup, $) {
                @Override
                protected TaskResult exec(SessionTaskRunner runner, Object input) {
                    final Version clientVersion = computeInstalledClientVersion($.sys);
                    final Version serverVersion = computeInstalledServerVersion(runner);

                    final boolean clientVersionOk = clientVersion != NOT_INSTALLED && $(versionConstraint).containsVersion(clientVersion);
                    final boolean serverVersionOk = serverVersion != NOT_INSTALLED && $(versionConstraint).containsVersion(clientVersion);

                    TaskResult r = TaskResult.OK;

                    if (!clientVersionOk || !serverVersionOk) {
                        $.sys.writeStringAs(new WriteStringInput("/etc/yum.repos.d/mongodb.repo", "" +
                            "[mongodb]\n" +
                            "name=MongoDB Repository\n" +
                            "baseurl=" + $(repo).get($.sys.getOsInfo().unixSubFlavour) + "\n" +
                            "gpgcheck=0\n" +
                            "enabled=1\n", true, Optional.<String>absent(), Optional.<String>absent()));
                    }

                    if (serverVersion == NOT_INSTALLED) {
                        r = Tasks.and(r, $.sys.getPackageManager().installPackage($(serverPackage)));
                    }

                    if(clientVersion == NOT_INSTALLED){
                        r = Tasks.and(r, $.sys.getPackageManager().installPackage($(clientPackage)));
                    }

                    $.sys.run($.sys.plainScript("service mongod start", true).timeoutForBuild().callback(sshPassword($)));

                    //TODO extract interface SystemService, ensure started: http://docs.mongodb.org/manual/tutorial/install-mongodb-on-red-hat-centos-or-fedora-linux/

                    return r;
                }

                @Override
                public Dependency asInstalledDependency() {
                    return new Dependency("mongo", $).addCommands("mongo --version");
                }
            };
        }
    });

    public Task<TaskDef> scriptTask(final String script,  Task parent, final TaskDef def, final SessionContext $){
        return new Task<TaskDef>(parent, def, $) {
            @Override
            protected TaskResult exec(SessionTaskRunner runner, Object input) {
                return runScript($, script);
            }
        };
    }

    public TaskResult runScript(SessionContext $, String script) {
        final String tempPath = $.var($.bear.randomFilePath).getTempPath("mongo_", ".js");

        Result result = $.sys.writeString(tempPath, script);

        if(!result.ok()){
            return new TaskResult(result);
        }

        CommandLineResult lineResult = $.sys.captureResult("mongo " + $.var(connectionString) + " " + tempPath, sshPassword($));

        $.sys.rm(newRm(tempPath));

        if(lineResult.text!=null
            && lineResult.text.contains("doesn't exist")
            && lineResult.text.contains("failed to load")
            ) {
            lineResult.setException(new Bear.ValidationException("failed to load " + tempPath));
        }

        return lineResult;
    }

    public Version computeInstalledClientVersion(SystemSession system) {
        String version;
        try {
            final String s = system.capture("mongo --version");
            if (s != null) {
                //MongoDB shell version: 2.4.8

                final Matcher matcher = Pattern.compile(".*version:\\s+([^\\s]+)").matcher(s);
                if (matcher.find()) {
                    version = matcher.group(1);
                } else {
                    version = null;
                }
            } else {
                version = null;
            }
        } catch (Bear.ValidationException e) {
            version = null;
        }

        return version == null ? NOT_INSTALLED : Versions.newVersion(version);
    }

    private Version computeInstalledServerVersion(SessionTaskRunner runner) {
        try {
            final CommandLineResult r = new CommandLineResult("mongo version", "", Result.ERROR);

            if (r.getResult().nok() || StringUtils.isBlank(r.text)) {
                return NOT_INSTALLED;
            }

            return Versions.newVersion(r.text.trim().split("\\s+")[1]);
        } catch (Bear.ValidationException e) {
            return NOT_INSTALLED;
        }
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return setup;
    }


}
