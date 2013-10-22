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
import bear.core.Bear;
import bear.core.SessionContext;
import bear.core.GlobalContext;
import bear.core.VarFun;
import bear.plugins.AbstractContext;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.plugins.sh.SystemSession;
import bear.session.*;
import bear.task.*;
import bear.plugins.Plugin;
import bear.vcs.CommandLineResult;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bear.task.TaskResult.and;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class MySqlPlugin extends Plugin<Task, TaskDef<?>> {
    private static final Logger logger = LoggerFactory.getLogger(MySqlPlugin.class);

    public final DynamicVariable<String>
        version = Variables.strVar().setDesc("null means ANY").defaultTo(null),
        adminUser = Variables.strVar().setDesc("admin user").defaultTo("root"),
        adminPassword = Variables.strVar().setDesc("admin password").defaultTo("root"),
        dbName = Variables.strVar().setDesc("database name"),
        user = Variables.strVar().setDesc("default user for operations").setEqualTo(adminUser),
        password = Variables.strVar().setDesc("pw").setEqualTo(adminPassword),
        serverPackage = Variables.newVar("mysql55-server"),
        clientPackage = Variables.newVar("mysql55"),
        mysqlTempScriptName = Variables.strVar().defaultTo("temp.sql"),
        mysqlTempScriptPath = Variables.dynamic(new VarFun<String, SessionContext>() {
            @Override
            public String apply(SessionContext $) {
                return $.sys.joinPath($.var(bear.projectSharedPath), $.var(mysqlTempScriptName));
            }
        }),
        dumpName = Variables.dynamic(new VarFun<String, AbstractContext>() {
            @Override
            public String apply(AbstractContext $) {
                return String.format("dump_%s_%s.GMT_%s.sql",
                    $.var(bear.applicationName), Bear.RELEASE_FORMATTER.print(new DateTime()), $.var(bear.sessionHostname));
            }
        }),
        dumpsDirPath = BearVariables.joinPath(bear.projectSharedPath, "dumps"),
        dumpPath = Variables.dynamic(new VarFun<String, SessionContext>() {
            public String apply(SessionContext $) {
                return $.sys.joinPath($.var(dumpsDirPath), $.var(dumpName) + ".bz2");
            }
        });

    public final DynamicVariable<Version> getVersion = Variables.dynamic(new VarFun<Version, AbstractContext>() {
        @Override
        public Version apply(AbstractContext $) {
            return Version.fromString($.var(version));
        }
    });

    public MySqlPlugin(GlobalContext global) {
        super(global);
    }

    public final InstallationTaskDef setup = new InstallationTaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {
                @Override
                protected TaskResult exec(TaskRunner runner) {
                    final Version version = computeRealClientVersion($.sys);

                    final boolean installedVersionOk = version != null && $(getVersion).matches(version);

                    TaskResult r = TaskResult.OK;

                    if (!installedVersionOk) {
                        $.sys.sudo().sendCommand($.sys.newCommandLine().sudo().addSplit("rpm -Uvh http://mirror.webtatic.com/yum/el6/latest.rpm"));
                        r = and(r, $.sys.getPackageManager().installPackage(new SystemEnvironmentPlugin.PackageInfo($(clientPackage))));
                    }

                    Version serverVersion = computeRealServerVersion(runner);

                    if (serverVersion == null) {
                        r = and(r, $.sys.getPackageManager().installPackage(new SystemEnvironmentPlugin.PackageInfo($(serverPackage))));
                    }

                    $.sys.sudo().sendCommand($.sys.newCommandLine().timeoutSec(30).sudo().addSplit("service mysqld start"));

                    $.sys.sudo().sendCommand($.sys.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} password", $(adminUser))).addRaw("'" + $(adminPassword) + "'"));
                    $.sys.sudo().sendCommand($.sys.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} -h {1} password",
                        $(adminUser), $(bear.sessionHostname))).addRaw("'" + $(adminPassword) + "'"));

                    final String createDatabaseSql = MessageFormat.format(
                        "CREATE DATABASE {0};\n" +
                            "GRANT ALL PRIVILEGES ON {0}.* TO {1}@localhost IDENTIFIED BY '{2}';\n",
                        $(dbName),
                        $(user),
                        $(password)
                    );

                    r = and(r, runScript(runner, createDatabaseSql,
                        $(adminUser),
                        $(adminPassword)
                    ));

                    return r;
                }
            };
        }
    };

    private Version computeRealServerVersion(TaskRunner runner) {
        final CommandLineResult r = runScript(runner, "select version();");

        if (r.result.nok() || StringUtils.isBlank(r.text)) {
            return null;
        }

        return Version.newVersion(r.text.trim().split("\\s+")[1]);
    }

    public Version computeRealClientVersion(SystemSession system) {
        final CommandLineResult result = system.sendCommand(system.newCommandLine().a("mysql", "--version"));

        final String version;
        if (result.text != null) {
            final Matcher matcher = Pattern.compile(".*Distrib\\s+([0-9.]+).*").matcher(result.text);
            if (matcher.matches()) {
                version = matcher.group(1);
            } else {
                version = null;
            }
        } else {
            version = null;
        }

        return version == null ? null : Version.newVersion(version);
    }


    public final TaskDef getUsers = new TaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {
                @Override
                protected TaskResult exec(TaskRunner runner) {
                    return runScript(runner, "SELECT User FROM mysql.user;");
                }
            };
        }
    };

    public final TaskDef runScript = new TaskDef() {

        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {
                @Override
                protected TaskResult exec(TaskRunner runner) {
                    final String s = Question.freeQuestion("Enter sql to execute: ");

                    return runScript(runner, s);
                }
            };
        }
    };

    public final TaskDef createDump = new TaskDef() {


        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {
                @Override
                protected TaskResult exec(TaskRunner runner) {
                    Question.freeQuestionWithOption("Enter a filename", $(dumpName), dumpName);

                    $.sys.mkdirs($(dumpsDirPath));

                    $.sys.sendCommand($.sys.line()
                        .stty()
                        .addSplit(String.format("mysqldump --user=%s -p %s", $(user), $(dbName)))
                        .pipe()
                        .addSplit("bzip2 --best")
                        .redirectTo($(dumpPath))
                        , mysqlPasswordCallback($(password)));

                    return TaskResult.OK;
                }
            };
        }
    };

    public final TaskDef createAndFetchDump = new TaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {
                @Override
                protected TaskResult exec(TaskRunner runner) {
                    runner.run(createDump);

                    $.sys.download($(dumpPath));

                    return TaskResult.OK;
                }
            };
        }
    };

    public final TaskDef restoreDump = new TaskDef() {
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return new Task<TaskDef>(parent, this, $) {
                @Override
                protected TaskResult exec(TaskRunner runner) {
                    Question.freeQuestionWithOption("Enter a filepath", $(dumpName), dumpName);

                    return $.sys.sendCommand($.sys.line()
                        .addSplit(String.format("bzcat %s", $(dumpPath)))
                        .pipe()
                        .addSplit(String.format("mysql --user=%s -p %s", $(user), $(dbName))),
                        mysqlPasswordCallback($(password)));
                }
            };
        }
    };


    public CommandLineResult runScript(TaskRunner runner, String sql) {
        return runScript(runner, sql, runner.$(user), runner.$(password));
    }

    public CommandLineResult runScript(TaskRunner runner, String sql, String user, final String pw) {
        final String filePath = runner.$(mysqlTempScriptPath);

        final SystemSession sys = runner.$().sys;
        sys.writeString(filePath, sql);

        return sys.sendCommand(sys.newCommandLine().stty().a("mysql", "-u", user, "-p").redirectFrom(filePath), mysqlPasswordCallback(pw));
    }

    private static ConsoleCallback mysqlPasswordCallback(final String pw) {
        return new ConsoleCallback(){
            @Override
            public void progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                if (buffer.contains("Enter password:")) {
                    console.println(pw);
                }
            }
        };
    }


    @Override
    public InstallationTaskDef getInstall() {
        return setup;
    }
}
