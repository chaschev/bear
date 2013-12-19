/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.plugins.mysql;

import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.Bear;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.plugins.sh.PackageInfo;
import bear.plugins.sh.SystemSession;
import bear.session.*;
import bear.task.*;
import bear.vcs.CommandLineResult;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bear.session.Variables.strVar;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class MySqlPlugin extends Plugin<Task, TaskDef<?>> {
    private static final Logger logger = LoggerFactory.getLogger(MySqlPlugin.class);

    public final DynamicVariable<String>
        version = strVar().desc("null means ANY").defaultTo(null),
        adminUser = strVar().desc("admin user").defaultTo("root"),
        adminPassword = strVar().desc("admin password").defaultTo("root"),
        dbName = strVar().desc("database name"),
        user = strVar().desc("default user for operations").setEqualTo(adminUser),
        password = strVar().desc("pw").setEqualTo(adminPassword),
        serverPackage = Variables.newVar("mysql55-server"),
        clientPackage = Variables.newVar("mysql55"),
        mysqlTempScriptName = strVar().defaultTo("temp.sql"),
        mysqlTempScriptPath = Variables.dynamic(new Fun<SessionContext, String>() {
            @Override
            public String apply(SessionContext $) {
                return $.sys.joinPath($.var(bear.projectSharedPath), $.var(mysqlTempScriptName));
            }
        }),
        dumpName = Variables.dynamic(new Fun<AbstractContext, String>() {
            @Override
            public String apply(AbstractContext $) {
                return String.format("dump_%s_%s.GMT_%s.sql",
                    $.var(bear.name), Bear.RELEASE_FORMATTER.print(new DateTime()), $.var(bear.sessionHostname));
            }
        }),
        dumpsDirPath = BearVariables.joinPath(bear.projectSharedPath, "dumps"),
        dumpPath = Variables.dynamic(new Fun<SessionContext, String>() {
            public String apply(SessionContext $) {
                return $.sys.joinPath($.var(dumpsDirPath), $.var(dumpName) + ".bz2");
            }
        });

    public final DynamicVariable<VersionConstraint> getVersion = Variables.dynamic(new Fun<AbstractContext, VersionConstraint>() {
        @Override
        public VersionConstraint apply(AbstractContext $) {
            return Versions.newVersionConstraint($.var(version));
        }
    });

    public MySqlPlugin(GlobalContext global) {
        super(global);
    }

    public final InstallationTaskDef setup = new InstallationTaskDef(new SingleTaskSupplier() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new InstallationTask<InstallationTaskDef>(parent, (InstallationTaskDef) def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    final Version version = computeInstalledClientVersion($.sys);

                    final boolean installedVersionOk = version != null && $(getVersion).containsVersion(version);

                    TaskResult r = TaskResult.OK;

                    if (!installedVersionOk) {
                        $.sys.sendCommand($.sys.line().sudo().addSplit("rpm -Uvh http://mirror.webtatic.com/yum/el6/latest.rpm"));
                        r = Tasks.and(r, $.sys.getPackageManager().installPackage(new PackageInfo($(clientPackage))));
                    }

                    Version serverVersion = computeInstalledServerVersion(runner);

                    if (serverVersion == null) {
                        r = Tasks.and(r, $.sys.getPackageManager().installPackage(new PackageInfo($(serverPackage))));
                    }

                    $.sys.script()
                    .line().sudo().addRaw("service mysqld start").build()
                    .line().sudo().addRaw("mysqladmin -u %s password '%s'", $(adminUser), $(adminPassword)).build()
                    .line().sudo().addRaw("mysqladmin -u %s -h %s password '%s'", $(adminUser), $(bear.sessionHostname), $(adminPassword));

                    final String createDatabaseSql = MessageFormat.format("" +
                            "CREATE DATABASE {0};\n" +
                            "GRANT ALL PRIVILEGES ON {0}.* TO {1}@localhost IDENTIFIED BY '{2}';\n",
                        $(dbName), $(user), $(password));

                    r = Tasks.and(r, runScript(runner, createDatabaseSql, $(adminUser), $(adminPassword)));

                    return r;
                }

                @Override
                public Dependency asInstalledDependency() {
                    return Dependency.NONE; //TODO FIX
                }
            };
        }
    }) ;

    private Version computeInstalledServerVersion(SessionRunner runner) {
        final CommandLineResult r = runScript(runner, "select version();");

        if (r.getResult().nok() || StringUtils.isBlank(r.output)) {
            return null;
        }

        return Versions.newVersion(r.output.trim().split("\\s+")[1]);
    }

    public Version computeInstalledClientVersion(SystemSession system) {
        final CommandLineResult result = system.sendCommand(system.newCommandLine().a("mysql", "--version"));

        final String version;
        if (result.output != null) {
            final Matcher matcher = Pattern.compile(".*Distrib\\s+([0-9.]+).*").matcher(result.output);
            if (matcher.matches()) {
                version = matcher.group(1);
            } else {
                version = null;
            }
        } else {
            version = null;
        }

        return version == null ? null : Versions.newVersion(version);
    }


    public final TaskDef<Task> getUsers = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new Task<TaskDef>(parent, def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    return runScript(runner, "SELECT User FROM mysql.user;");
                }
            };

        }
    }) ;

    public final TaskDef<Task> runScript = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new Task<TaskDef>(parent, def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    final String s = Question.freeQuestion("Enter sql to execute: ");

                    return runScript(runner, s);
                }
            };

        }
    });

    public final TaskDef<Task> createDump = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
            return new Task<TaskDef>(parent, def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    Question.freeQuestionWithOption("Enter a filename", $(dumpName), dumpName);

                    $.sys.mkdirs($(dumpsDirPath)).run();

                    $.sys.sendCommand($.sys.line()
                        .stty()
                        .addSplit(String.format("mysqldump --user=%s -p %s", $(user), $(dbName)))
                        .pipe()
                        .addSplit("bzip2 --best")
                        .timeoutForInstallation()
                        .redirectTo($(dumpPath))
                    );

                    return TaskResult.OK;
                }
            };
        }
    }) ;

    public final TaskDef createAndFetchDump = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    runner.run(createDump);

                    $.sys.download($(dumpPath));

                    return TaskResult.OK;
                }
            };
        }
    });

    public final TaskDef restoreDump = new TaskDef<Task>(new SingleTaskSupplier<Task>() {
        @Override
        public Task createNewSession(SessionContext $, Task parent, TaskDef<Task> def) {
            return new Task<TaskDef>(parent, def, $) {
                @Override
                protected TaskResult exec(SessionRunner runner, Object input) {
                    Question.freeQuestionWithOption("Enter a filepath", $(dumpName), dumpName);

                    return $.sys.script().line()
                        .addRaw("bzcat %s | mysql --user=%s -p %s", $(dumpPath), $(user), $(dbName))
                        .timeoutForInstallation()
                        .build().run()
                    ;
                }
            };

        }
    });


    public CommandLineResult runScript(SessionRunner runner, String sql) {
        return runScript(runner, sql, runner.$(user), runner.$(password));
    }

    public CommandLineResult runScript(SessionRunner runner, String sql, String user, final String pw) {
        final String filePath = runner.$(mysqlTempScriptPath);

        final SystemSession sys = runner.$().sys;

        sys.writeString(sql).toPath(filePath).run();

        return sys.sendCommand(sys.line().stty().a("mysql", "-u", user, "-p").redirectFrom(filePath));
    }

    public static ConsoleCallback passwordCallback(final String pw) {
        return new ConsoleCallback(){
            @Override
            @Nonnull
            public ConsoleCallbackResult progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                if (buffer.contains("Enter password:")) {
                    console.println(pw);
                }

                return ConsoleCallbackResult.CONTINUE;
            }
        };
    }


    @Override
    public InstallationTaskDef getInstall() {
        return setup;
    }
}
