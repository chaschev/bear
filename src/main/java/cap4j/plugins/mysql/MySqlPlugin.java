package cap4j.plugins.mysql;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.core.VarFun;
import cap4j.plugins.Plugin;
import cap4j.scm.CommandLineResult;
import cap4j.session.*;
import cap4j.task.Task;
import cap4j.task.TaskResult;
import cap4j.task.TaskRunner;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cap4j.core.CapConstants.*;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:24 PM
*/
public class MySqlPlugin extends Plugin {
    private static final Logger logger = LoggerFactory.getLogger(MySqlPlugin.class);

    public final DynamicVariable<String>
        version = strVar().setDesc("null means ANY").defaultTo(null),
        adminUser = strVar().setDesc("admin user").defaultTo("root"),
        adminPassword = strVar().setDesc("admin password").defaultTo("root"),
        dbName = strVar().setDesc("database name"),
        user = strVar().setDesc("default user for operations").setEqualTo(adminUser),
        password = strVar().setDesc("pw").setEqualTo(adminPassword),
        serverPackage = newVar("mysql55-server"),
        clientPackage = newVar("mysql55"),
        mysqlTempScriptName = strVar().defaultTo("temp.sql"),
        mysqlTempScriptPath = dynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return ctx.system.joinPath(ctx.var(cap.sharedPath), ctx.var(mysqlTempScriptName));
            }
        }),
        dumpName = dynamic(new VarFun<String>() {
            @Override
            public String apply() {
                return String.format("dump_%s_%s.GMT_%s.sql",
                    ctx.var(cap.applicationName), CapConstants.RELEASE_FORMATTER.print(new DateTime()), ctx.var(cap.sessionHostname));
            }
        }),
        dumpsDirPath = VariableUtils.joinPath(cap.sharedPath, "dumps"),
        dumpPath = dynamic(new VarFun<String>() {
            public String apply() {
                return ctx.system.joinPath(ctx.var(dumpsDirPath), ctx.var(dumpName) + ".bz2");
            }
        });

    public final DynamicVariable<Version> getVersion = dynamic(new VarFun<Version>() {
        @Override
        public Version apply() {
            return Version.fromString(ctx.var(version));
        }
    });

    public MySqlPlugin(GlobalContext global) {
        super(global);
    }

    public final Task setup = new Task("setup mysql") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            final Version version = computeRealClientVersion(system);

            final boolean installedVersionOk = version != null && ctx.var(getVersion).matches(version);

            Result r = Result.OK;

            if(!installedVersionOk){
                system.sudo().run(system.newCommandLine().sudo().addSplit("rpm -Uvh http://mirror.webtatic.com/yum/el6/latest.rpm"));
                r = system.getPackageManager().installPackage(new SystemEnvironment.PackageInfo(ctx.var(clientPackage))).result;
            }

            Version serverVersion = computeRealServerVersion(runner);

            if(serverVersion == null){
                r = system.getPackageManager().installPackage(new SystemEnvironment.PackageInfo(ctx.var(serverPackage))).result;
            }

            system.sudo().run(system.newCommandLine().timeoutSec(30).sudo().addSplit("service mysqld start"));

            system.sudo().run(system.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} password", ctx.var(MySqlPlugin.this.adminUser))).addRaw("'" + ctx.var(MySqlPlugin.this.adminPassword) + "'"));
            system.sudo().run(system.newCommandLine().sudo().addSplit(MessageFormat.format("mysqladmin -u {0} -h {1} password",
                ctx.var(MySqlPlugin.this.adminUser), ctx.var(cap.sessionHostname))).addRaw("'" + ctx.var(MySqlPlugin.this.adminPassword) + "'"));

            final String createDatabaseSql = MessageFormat.format(
                "CREATE DATABASE {0};\n" +
                    "GRANT ALL PRIVILEGES ON {0}.* TO {1}@localhost IDENTIFIED BY '{2}';\n",
                ctx.var(MySqlPlugin.this.dbName),
                ctx.var(MySqlPlugin.this.user),
                ctx.var(MySqlPlugin.this.password)
            );

            r = Result.and(r, runScript(runner, createDatabaseSql,
                ctx.var(MySqlPlugin.this.adminUser),
                ctx.var(MySqlPlugin.this.adminPassword)
            ).result);

            return new TaskResult(r);
        }
    };

    public void init(){

    }

    private Version computeRealServerVersion(TaskRunner runner) {
        final CommandLineResult r = runScript(runner, "select version();");

        if(r.result.nok() || StringUtils.isBlank(r.text)){
            return null;
        }

        return Version.newVersion(r.text.trim().split("\\s+")[1]);
    }

    public Version computeRealClientVersion(SystemEnvironment system){
        final CommandLineResult result = system.run(system.newCommandLine().a("mysql", "--version"));

        final String version;
        if(result.text != null){
            final Matcher matcher = Pattern.compile(".*Distrib\\s+([0-9.]+).*").matcher(result.text);
            if(matcher.matches()){
                version = matcher.group(1);
            }else{
                version = null;
            }
        }else{
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

    public final Task createDump = new Task("create dump") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            Question.freeQuestionWithOption("Enter a filename", ctx.var(dumpName), dumpName);

            system.mkdirs(ctx.var(dumpsDirPath));

            system.run(system.newCommandLine()
                .stty()
                .addSplit(String.format("mysqldump --user=%s -p %s", ctx.var(user), ctx.var(dbName)))
                .pipe()
                .addSplit("bzip2 --best")
                .redirectTo(ctx.var(dumpPath))
                , mysqlPasswordCallback(ctx.var(password)));

            return new TaskResult(Result.OK);
        }
    };

    public final Task createAndFetchDump = new Task("create & fetch a dump") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            runner.run(createDump);

            system.download(ctx.var(dumpPath));

            return new TaskResult(Result.OK);
        }
    };

    public final Task restoreDump = new Task("create & fetch a dump") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            Question.freeQuestionWithOption("Enter a filepath", ctx.var(dumpName), dumpName);

            final CommandLineResult r = system.run(system.newCommandLine()
                .addSplit(String.format("bzcat %s", ctx.var(dumpPath)))
                .pipe()
                .addSplit(String.format("mysql --user=%s -p %s", ctx.var(user), ctx.var(dbName))),
                mysqlPasswordCallback(ctx.var(password)));

            return new TaskResult(r);
        }
    };


    public CommandLineResult runScript(TaskRunner runner, String sql) {
        return runScript(runner, sql, runner.ctx.var(user), runner.ctx.var(password));
    }

    public CommandLineResult runScript(TaskRunner runner, String sql, String user, final String pw){
        final String filePath = runner.ctx.var(mysqlTempScriptPath);

        final SystemEnvironment sys = runner.ctx.system;
        sys.writeString(filePath, sql);

        return sys.run(sys.newCommandLine().stty().a("mysql", "-u", user, "-p").redirectFrom(filePath), mysqlPasswordCallback(pw));
    }

    private static GenericUnixRemoteEnvironment.SshSession.WithSession mysqlPasswordCallback(final String pw) {
        return new GenericUnixRemoteEnvironment.SshSession.WithSession(null, pw) {
            @Override
            public void act(Session session, Session.Shell shell) throws Exception {
                if(text.contains("Enter password:")){
                    final OutputStream os = session.getOutputStream();
                    os.write((pw + "\n").getBytes(IOUtils.UTF8));
                    os.flush();
                }
            }
        };
    }



}
