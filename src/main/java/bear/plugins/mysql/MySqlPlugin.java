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
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.VarFun;
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
public class MySqlPlugin extends Plugin {
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
        mysqlTempScriptPath = Variables.dynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return $.sys.joinPath($(bear.projectSharedPath), $(mysqlTempScriptName));
            }
        }),
        dumpName = Variables.dynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return String.format("dump_%s_%s.GMT_%s.sql",
                    $(bear.applicationName), Bear.RELEASE_FORMATTER.print(new DateTime()), $(bear.sessionHostname));
            }
        }),
        dumpsDirPath = Variables.joinPath(bear.projectSharedPath, "dumps"),
        dumpPath = Variables.dynamic(new VarFun<String>() {
            public String apply() {
                return $.sys.joinPath($(dumpsDirPath), $(dumpName) + ".bz2");
            }
        });

    public final DynamicVariable<Version> getVersion = Variables.dynamic(new VarFun<Version>() {
        @Override
        public Version apply() {
            return Version.fromString($(version));
        }
    });

    public MySqlPlugin(GlobalContext global) {
        super(global);
    }

    public final InstallationTaskDef setup = new InstallationTaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(this, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    final Version version = computeRealClientVersion($.sys);

                    final boolean installedVersionOk = version != null && $(getVersion).matches(version);

                    TaskResult r = TaskResult.OK;

                    if (!installedVersionOk) {
                        $.sys.sudo().run($.sys.newCommandLine().sudo().addSplit("rpm -Uvh http://mirror.webtatic.com/yum/el6/latest.rpm"));
                        r = and(r, $.sys.getPackageManager().installPackage(new SystemEnvironment.PackageInfo($(clientPackage))));
                    }

                    Version serverVersion = computeRealServerVersion(runner);

                    if (serverVersion == null) {
                        r = and(r, $.sys.getPackageManager().installPackage(new SystemEnvironment.PackageInfo($(serverPackage))));
                    }

                    $.sys.sudo().run($.sys.newCommandLine().timeoutSec(30).sudo().addSplit("service mysqld start"));

                    $.sys.sudo().run($.sys.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} password", $(adminUser))).addRaw("'" + $(adminPassword) + "'"));
                    $.sys.sudo().run($.sys.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} -h {1} password",
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

    public void initPlugin() {

    }

    private Version computeRealServerVersion(TaskRunner runner) {
        final CommandLineResult r = runScript(runner, "select version();");

        if (r.result.nok() || StringUtils.isBlank(r.text)) {
            return null;
        }

        return Version.newVersion(r.text.trim().split("\\s+")[1]);
    }

    public Version computeRealClientVersion(SystemEnvironment system) {
        final CommandLineResult result = system.run(system.newCommandLine().a("mysql", "--version"));

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
        public Task newSession(SessionContext $) {
            return new Task(this, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    return runScript(runner, "SELECT User FROM mysql.user;");
                }
            };
        }
    };

    public final TaskDef runScript = new TaskDef() {

        @Override
        public Task newSession(SessionContext $) {
            return new Task(this, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    final String s = Question.freeQuestion("Enter sql to execute: ");

                    return runScript(runner, s);
                }
            };
        }
    };

    public final TaskDef createDump = new TaskDef() {


        @Override
        public Task newSession(SessionContext $) {
            return new Task(this, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
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
        public Task newSession(SessionContext $) {
            return new Task(this, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
                    runner.run(createDump);

                    $.sys.download($(dumpPath));

                    return TaskResult.OK;
                }
            };
        }
    };

    public final TaskDef restoreDump = new TaskDef() {
        @Override
        public Task newSession(SessionContext $) {
            return new Task(this, $) {
                @Override
                protected TaskResult run(TaskRunner runner) {
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

        final SystemEnvironment sys = runner.$().sys;
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
