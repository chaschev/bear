package bear.plugins.mongo;

import bear.annotations.Shell;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.except.ValidationException;
import bear.plugins.Plugin;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.plugins.sh.SystemSession;
import bear.plugins.sh.UnixFlavour;
import bear.plugins.sh.WriteStringResult;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.Versions;
import bear.task.*;
import bear.vcs.CommandLineResult;
import com.google.common.base.Function;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bear.session.Variables.*;
import static bear.session.Versions.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Shell("mongo")
public class MongoDbPlugin extends Plugin {
    private static class PackageInfo{
        String serverPackage;
        String clientPackage;

        private PackageInfo(String serverPackage, String clientPackage) {
            this.serverPackage = serverPackage;
            this.clientPackage = clientPackage;
        }
    }

    public final DynamicVariable<String>
        version = undefined(),
        dbHost = newVar("localhost").desc("database host"),
        dbPort = newVar("27017").desc("database port"),
        dbName = strVar().desc("database name"),
        connectionString = concat(dbHost, ":", dbPort, "/", dbName),
        serviceName = dynamic(new Fun<SessionContext, String>() {
            @Override
            public String apply(SessionContext $) {
                return $.sys.getOsInfo().unixFlavour == UnixFlavour.UBUNTU ? "mongodb" : "mongod";
            }
        });

    public final DynamicVariable<PackageInfo> packageInfo = dynamic(new Fun<SessionContext, PackageInfo>() {
        @Override
        public PackageInfo apply(SessionContext $) {
            switch ($.sys.getOsInfo().unixFlavour) {
                case CENTOS:
                    return new PackageInfo("mongo-10gen-server", "mongo-10gen");

                case UBUNTU:
                    return  new PackageInfo("mongodb-10gen-server", "mongodb-10gen");
            }
            throw new IllegalStateException();
        }
    });

    public final DynamicVariable<CommandLineResult<?>> installer = dynamic(new Fun<SessionContext, CommandLineResult<?>>() {
        @Override
        public CommandLineResult<?> apply(SessionContext $) {
            switch ($.sys.getOsInfo().unixFlavour) {
                case CENTOS:
                    String script = "" +
                        "[mongodb]\n" +
                        "name=MongoDB Repository\n" +
                        "baseurl=http://downloads-distro.mongodb.org/repo/redhat/os/x86_64/\n" +
                        "gpgcheck=0\n" +
                        "enabled=1\n";

                    $.sys.writeString(script).toPath("/etc/yum.repos.d/mongodb.repo").sudo().run();
                    break;
                case UBUNTU:
                    $.sys.captureResult("apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 7F0CEB10", true).throwIfException();
                    $.sys.writeString("deb http://downloads-distro.mongodb.org/repo/ubuntu-upstart dist 10gen\n").toPath("/etc/apt/sources.list.d/mongodb.list").sudo().run();
                    $.sys.captureResult("apt-get update", true).throwIfError();

                    break;
            }

            return CommandLineResult.OK;
        }
    });

    public final DynamicVariable<VersionConstraint> versionConstraint = condition(isSet(version), convert(version, new Function<String, VersionConstraint>() {
        public VersionConstraint apply(String input) {
            return newVersionConstraint(input);
        }
    }), newVar(ANY));

    public MongoDbPlugin(GlobalContext global) {
        super(global);

        shell = new MongoDbShellMode(MongoDbPlugin.this);
    }

    public final InstallationTaskDef<InstallationTask> setup = new InstallationTaskDef<InstallationTask>(new SingleTaskSupplier<Object, TaskResult<?>>() {
        @Override
        public Task<Object, TaskResult<?>> createNewSession(SessionContext $, Task<Object, TaskResult<?>> parent, TaskDef<Object, TaskResult<?>> def) {
            return new InstallationTask<InstallationTaskDef>(parent, setup, $) {
                @Override
                protected TaskResult<?> exec(SessionRunner runner) {
                    final Version clientVersion = computeInstalledClientVersion($.sys);
                    final Version serverVersion = computeInstalledServerVersion(runner);

                    final boolean clientVersionOk = clientVersion != NOT_INSTALLED && $(versionConstraint).containsVersion(clientVersion);
                    final boolean serverVersionOk = serverVersion != NOT_INSTALLED && $(versionConstraint).containsVersion(clientVersion);

                    TaskResult<?> r = TaskResult.OK;

                    if (!clientVersionOk || !serverVersionOk) {
                       r = $(installer);
                    }

                    PackageInfo info = $.var(packageInfo);

                    SystemEnvironmentPlugin.PackageManager packageManager = $.sys.getPackageManager();

                    if (serverVersion == NOT_INSTALLED) {
                        r = Tasks.and(r, packageManager.installPackage(info.serverPackage));
                    }

                    if(clientVersion == NOT_INSTALLED){
                        r = Tasks.and(r, packageManager.installPackage(info.clientPackage));
                    }

                    packageManager.serviceCommand($(serviceName), "start");

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

    public Task<Object, TaskResult<?>> scriptTask(final String script,  Task parent, final TaskDef def, final SessionContext $){
        return new Task<Object, TaskResult<?>>(parent, def, $) {
            @Override
            protected TaskResult<?> exec(SessionRunner runner) {
                return runScript($, script);
            }
        };
    }

    public TaskResult<?> runScript(SessionContext $, String script) {
        final String tempPath = $.var($.bear.randomFilePath).getTempPath("mongo_", ".js");

        WriteStringResult result = $.sys.writeString(script).toPath(tempPath).run();

        if(!result.ok()){
            return TaskResult.value(result);
        }

        CommandLineResult<?> lineResult = $.sys.captureBuilder("mongo " + $.var(connectionString) + " " + tempPath).run();

        $.sys.rm(tempPath).run();

        if(lineResult.output !=null
            && lineResult.output.contains("doesn't exist")
            && lineResult.output.contains("failed to load")
            ) {
            lineResult.setException(new ValidationException("failed to load " + tempPath));
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
        } catch (ValidationException e) {
            version = null;
        }

        return version == null ? NOT_INSTALLED : Versions.newVersion(version);
    }

    private Version computeInstalledServerVersion(SessionRunner runner) {
        try {
            final CommandLineResult<? extends CommandLineResult> r = new CommandLineResult("mongo version", "", Result.ERROR);

            if (r.getResult().nok() || StringUtils.isBlank(r.output)) {
                return NOT_INSTALLED;
            }

            return Versions.newVersion(r.output.trim().split("\\s+")[1]);
        } catch (ValidationException e) {
            return NOT_INSTALLED;
        }
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return setup;
    }


}
