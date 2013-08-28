package cap4j.core;

import cap4j.plugins.Plugin;
import cap4j.scm.BranchInfoResult;
import cap4j.scm.CommandLine;
import cap4j.scm.SvnVcsCLI;
import cap4j.scm.VcsCLI;
import cap4j.session.DynamicVariable;
import cap4j.strategy.BaseStrategy;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.ArrayUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

import static cap4j.session.VariableUtils.*;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class CapConstants {
    public static final DateTimeZone GMT = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
    public static final DateTimeFormatter RELEASE_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd.HHmmss").withZone(GMT);
    public final GlobalContext global;

    public CapConstants(GlobalContext global) {
        this.global = global;
        Plugin.nameVars(this);
    }

    public final DynamicVariable<String>

    applicationsPath = strVar("applicationsPath", "System apps folder").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            return ctx.system.isNativeUnix() ? "/var/lib" : "c:";
        }
    }),

    logsPath = strVar("applicationsPath", "System apps folder").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            return ctx.system.isNativeUnix() ? "/var/log" : "c:";
        }
    }),

    task = strVar("task", "A task to run").defaultTo("deploy"),

    applicationName = strVar().setDesc("Your app name"),
    appLogsPath = joinPath("appLogsPath", logsPath, applicationName),
    sshUsername = strVar("sshUsername", ""),
    appUsername = eql("appUsername", sshUsername),
    sshPassword = dynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            return global.getProperty(ctx.var(sessionHostname) + ".password");
        }
    }),
    stage = strVar("stage", "Stage to deploy to"),
    repositoryURI = strVar("repository", "Project VCS URI"),
    vcsType = enumConstant("vcsType", "Your VCS type", "svn"),
    vcsUsername = eql("vcsUserName", sshUsername),
    vcsPassword = eql("vcsPassword", sshPassword),
    sessionHostname = strVar("", "internal variable containing the name of the current session"),

    tempUserInput = strVar("tempUserInput", ""),

    deployScript = strVar("deployScript", "Script to use").defaultTo("CreateNewScript"),

    deployTo = joinPath("deployTo", applicationsPath, applicationName).setDesc("Current release dir"),

    currentDirName = strVar("currentDirName", "Current release dir").defaultTo("current"),
    sharedDirName = strVar("sharedDirName", "").defaultTo("shared"),

    releasesDirName = strVar("releasesDirName", "").defaultTo("releases"),

    releaseName = strVar("releaseName", "I.e. 20140216").defaultTo(RELEASE_FORMATTER.print(new DateTime()) + ".GMT"),

    devEnvironment = enumConstant("devEnvironment", "Development environment", "dev", "test", "prod").defaultTo("prod"),

    revision = strVar("revision", "Get head revision").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            return vcs.apply(ctx).head();
        }
    }),

   realRevision = strVar("realRevision", "Update revision from vcs").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            final VcsCLI vcsCLI = ctx.var(vcs);
            final CommandLine<BranchInfoResult> line = vcsCLI.queryRevision(ctx.var(revision), Collections.<String, String>emptyMap());

            line.timeoutMs(20000);

            BranchInfoResult r = ctx.system.run(line, vcsCLI.passwordCallback());

            return r.revision;
        }
    }),

    releasesPath = joinPath("releasesPath", deployTo, releasesDirName),
    currentPath = joinPath("currentPath", deployTo, currentDirName),
    sharedPath = joinPath("sharedPath", deployTo, sharedDirName),

    releasePath = joinPath("releasesPath", releasesPath, releaseName),

    vcsCheckoutPath = joinPath("vcsCheckoutPath", sharedPath, "vcs"),
    vcsBranchName = strVar("vcsBranchName", "").defaultTo("trunk"),
    vcsBranchLocalPath = joinPath("vcsBranchLocalPath", vcsCheckoutPath, vcsBranchName),
    vcsBranchURI = joinPath("vcsProjectURI", repositoryURI, vcsBranchName),

    getLatestReleasePath = strVar("getLatestReleasePath", "").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            final Releases r = ctx.var(getReleases);

            if (r.releases.isEmpty()) return null;

            return ctx.system.joinPath(ctx.var(releasesPath), r.last());
        }
    }).memoize(true),

    getPreviousReleasePath = strVar("getPreviousReleasePath", "").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            final Releases r = ctx.var(getReleases);

            if (r.releases.size() < 2) return null;

            return ctx.system.joinPath(ctx.var(releasesPath), r.previous());
        }
    }).memoize(true),

   getCurrentRevision = strVar("getCurrentRevision", "").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            return ctx.system.readString(ctx.joinPath(currentPath, "REVISION"), null);
        }
    }).memoize(true),

    getLatestReleaseRevision = strVar("getLatestReleaseRevision", "").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            return ctx.system.readString(ctx.joinPath(getLatestReleasePath, "REVISION"), null);
        }
    }).memoize(true),

     getPreviousReleaseRevision = strVar("getPreviousReleaseRevision", "").setDynamic(new Function<SessionContext, String>() {
        public String apply(SessionContext ctx) {
            return ctx.system.readString(ctx.joinPath(getPreviousReleasePath, "REVISION"), null);
        }
    }).memoize(true);

    public final DynamicVariable<Boolean>
        useSudo = bool("useSudo", "").defaultTo(true),
        productionDeployment = bool("productionDeployment", "").defaultTo(true),
        clean = eql("clean", productionDeployment),
        speedUpBuild = and("speedUpBuild", not("", productionDeployment), not("", clean)),
        scmAuthCache = dynamicNotSet("scmAuthCache", ""),
        scmPreferPrompt = dynamicNotSet("scmPreferPrompt", ""),
        isRemoteEnv = dynamic(new Function<SessionContext, Boolean>() {
            public Boolean apply(SessionContext input) {
                return input.system.isRemote();
            }
        }),
        isNativeUnix = dynamic(new Function<SessionContext, Boolean>() {
            public Boolean apply(SessionContext input) {
                return input.system.isNativeUnix();
            }
        })
    ;

    public static final DynamicVariable<Integer>
        keepXReleases = newVar(5);

    public final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new Function<SessionContext, Releases>() {
        public Releases apply(SessionContext ctx) {
            return new Releases(ctx.system.ls(ctx.var(releasesPath)));
        }
    });

    public final DynamicVariable<Stages> stages = new DynamicVariable<Stages>("stages", "List of stages. Stage is collection of servers with roles and auth defined for each of the server.");
    public final DynamicVariable<Stage> getStage = dynamic("getStage", "", new Function<SessionContext, Stage>() {
        public Stage apply(SessionContext ctx) {
            final String stageName = ctx.var(CapConstants.this.stage);
            final Stage stage = Iterables.find(ctx.var(stages).stages, new Predicate<Stage>() {
                public boolean apply(Stage s) {
                    return s.name.equals(stageName);
                }
            });

            stage.global = global;

            return stage;
        }
    });

    public final DynamicVariable<VcsCLI> vcs = new DynamicVariable<VcsCLI>("vcs", "VCS adapter").setDynamic(new Function<SessionContext, VcsCLI>() {
        public VcsCLI apply(SessionContext ctx) {
            final String scm = ctx.var(vcsType);

            if ("svn".equals(scm)) {
                return new SvnVcsCLI(ctx, global);
            }

            throw new UnsupportedOperationException(scm + " is not yet supported");
        }
    });

    public final DynamicVariable<File>
        scriptsDir = newVar(new File(".cap")),
        settingsFile = dynamic(new Function<SessionContext, File>() {
            public File apply(SessionContext ctx) {
                return new File(ctx.var(scriptsDir), "settings.properties");
            }
        })
    ;

    public static final DynamicVariable<BaseStrategy> newStrategy = dynamicNotSet("strategy", "Deployment strategy: how app files copied and built");

    public static <T> DynamicVariable<T> dynamicNotSet(String desc) {
        return dynamicNotSet(null, desc);
    }

    public static <T> DynamicVariable<T> dynamicNotSet(final String name, String desc) {
        return dynamic(name, desc, new Function<SessionContext, T>() {
            public T apply(SessionContext ctx) {
                throw new UnsupportedOperationException("you need to set the :!todo-link-function-to-var-to-get-it's-name!");
            }
        });
    }

    public static <T> DynamicVariable<T> newVar(T _default){
        return new DynamicVariable<T>("").defaultTo(_default);
    }

    public static <T> DynamicVariable<T> dynamic(Function<SessionContext, T> function){
        return dynamic(null, "", function);
    }

    public static <T> DynamicVariable<T> dynamic(String desc){
        return dynamic(null, desc);
    }

    static <T> DynamicVariable<T> dynamic(String name, String desc){
        return new DynamicVariable<T>(name, desc);
    }

    public static <T> DynamicVariable<T> dynamic(String desc, Function<SessionContext, T> function) {
        return new DynamicVariable<T>((String)null, desc).setDynamic(function);
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc, Function<SessionContext, T> function) {
        return new DynamicVariable<T>(name, desc).setDynamic(function);
    }

    private static DynamicVariable<String> enumConstant(String name, final String desc, final String... options) {
        return new DynamicVariable<String>(name, desc) {
            @Override
            public void validate(String value) {
                if (!ArrayUtils.contains(options, value)) {
                    Preconditions.checkArgument(false, ":" + name +
                        " must be one of: " + Arrays.asList(options));
                }
            }
        };
    }

    public static DynamicVariable<String> strVar() {
        return new DynamicVariable<String>((String) null, "");
    }

    public static DynamicVariable<String> strVar(String name, String desc) {
        return new DynamicVariable<String>(name, desc);
    }

    public static DynamicVariable<Boolean> bool(String name, String desc) {
        return new DynamicVariable<Boolean>(name, desc);
    }
}
