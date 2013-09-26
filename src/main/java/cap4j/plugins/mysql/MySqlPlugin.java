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

package cap4j.plugins.mysql;

import cap4j.core.Cap;
import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.Plugin;
import cap4j.scm.CommandLineResult;
import cap4j.scm.GitCLI;
import cap4j.session.*;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                return $.system.joinPath($.var(cap.sharedPath), $.var(mysqlTempScriptName));
            }
        }),
        dumpName = Variables.dynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return String.format("dump_%s_%s.GMT_%s.sql",
                    $.var(cap.applicationName), Cap.RELEASE_FORMATTER.print(new DateTime()), $.var(cap.sessionHostname));
            }
        }),
        dumpsDirPath = Variables.joinPath(cap.sharedPath, "dumps"),
        dumpPath = Variables.dynamic(new VarFun<String>() {
            public String apply() {
                return $.system.joinPath($.var(dumpsDirPath), $.var(dumpName) + ".bz2");
            }
        });

    public final DynamicVariable<Version> getVersion = Variables.dynamic(new VarFun<Version>() {
        @Override
        public Version apply() {
            return Version.fromString($.var(version));
        }
    });

    public MySqlPlugin(GlobalContext global) {
        super(global);
    }

    public final Task setup = new Task() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            final Version version = computeRealClientVersion(system);

            final boolean installedVersionOk = version != null && $.var(getVersion).matches(version);

            Result r = Result.OK;

            if (!installedVersionOk) {
                system.sudo().run(system.newCommandLine().sudo().addSplit("rpm -Uvh http://mirror.webtatic.com/yum/el6/latest.rpm"));
                r = system.getPackageManager().installPackage(new SystemEnvironment.PackageInfo($.var(clientPackage))).result;
            }

            Version serverVersion = computeRealServerVersion(runner);

            if (serverVersion == null) {
                r = system.getPackageManager().installPackage(new SystemEnvironment.PackageInfo($.var(serverPackage))).result;
            }

            system.sudo().run(system.newCommandLine().timeoutSec(30).sudo().addSplit("service mysqld start"));

            system.sudo().run(system.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} password", $.var(MySqlPlugin.this.adminUser))).addRaw("'" + $.var(MySqlPlugin.this.adminPassword) + "'"));
            system.sudo().run(system.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} -h {1} password",
                $.var(MySqlPlugin.this.adminUser), $.var(cap.sessionHostname))).addRaw("'" + $.var(MySqlPlugin.this.adminPassword) + "'"));

            final String createDatabaseSql = MessageFormat.format(
                "CREATE DATABASE {0};\n" +
                    "GRANT ALL PRIVILEGES ON {0}.* TO {1}@localhost IDENTIFIED BY '{2}';\n",
                $.var(MySqlPlugin.this.dbName),
                $.var(MySqlPlugin.this.user),
                $.var(MySqlPlugin.this.password)
            );

            r = Result.and(r, runScript(runner, createDatabaseSql,
                $.var(MySqlPlugin.this.adminUser),
                $.var(MySqlPlugin.this.adminPassword)
            ).result);

            return new TaskResult(r);
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


    public final Task getUsers = new Task() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runScript(runner, "SELECT User FROM mysql.user;"));
        }
    };

    public final Task runScript = new Task() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            final String s = Question.freeQuestion("Enter sql to execute: ");

            return new TaskResult(runScript(runner, s));
        }
    };

    public final Task createDump = new Task() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            Question.freeQuestionWithOption("Enter a filename", $.var(dumpName), dumpName);

            system.mkdirs($.var(dumpsDirPath));

            system.run(system.newCommandLine()
                .stty()
                .addSplit(String.format("mysqldump --user=%s -p %s", $.var(user), $.var(dbName)))
                .pipe()
                .addSplit("bzip2 --best")
                .redirectTo($.var(dumpPath))
                , mysqlPasswordCallback($.var(password)));

            return TaskResult.OK;
        }
    };

    public final Task createAndFetchDump = new Task() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            runner.run(createDump);

            system.download($.var(dumpPath));

            return TaskResult.OK;
        }
    };

    public final Task restoreDump = new Task() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            Question.freeQuestionWithOption("Enter a filepath", $.var(dumpName), dumpName);

            final CommandLineResult r = system.run(system.newCommandLine()
                .addSplit(String.format("bzcat %s", $.var(dumpPath)))
                .pipe()
                .addSplit(String.format("mysql --user=%s -p %s", $.var(user), $.var(dbName))),
                mysqlPasswordCallback($.var(password)));

            return new TaskResult(r);
        }
    };


    public CommandLineResult runScript(TaskRunner runner, String sql) {
        return runScript(runner, sql, runner.$.var(user), runner.$.var(password));
    }

    public CommandLineResult runScript(TaskRunner runner, String sql, String user, final String pw) {
        final String filePath = runner.$.var(mysqlTempScriptPath);

        final SystemEnvironment sys = runner.$.system;
        sys.writeString(filePath, sql);

        return sys.run(sys.newCommandLine().stty().a("mysql", "-u", user, "-p").redirectFrom(filePath), mysqlPasswordCallback(pw));
    }

    private static GenericUnixRemoteEnvironment.SshSession.WithSession mysqlPasswordCallback(final String pw) {
        return new GenericUnixRemoteEnvironment.SshSession.WithSession(null, pw) {
            @Override
            public void act(Session session, Session.Shell shell) throws Exception {
                if (text.contains("Enter password:")) {
                    GitCLI.answer(session, pw);
                }
            }
        };
    }


    @Override
    public Task getSetup() {
        return setup;
    }
}
