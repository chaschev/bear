package cap4j;

import cap4j.scm.BranchInfoResult;
import cap4j.scm.CommandLine;
import cap4j.scm.Vcs;
import cap4j.scm.SvnVcs;
import cap4j.session.DynamicVariable;
import cap4j.strategy.BaseStrategy;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.annotation.Nullable;
import java.util.*;

import static cap4j.GlobalContext.var;
import static cap4j.session.VariableUtils.*;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class CapConstants {
    public static final DateTimeZone GMT = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
    public static final DateTimeFormatter RELEASE_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd.HHmmss").withZone(GMT);

    public static class Releases {
        List<String> releases;

        public Releases(List<String> releases) {
            this.releases = releases;
        }

        public String last() {
            return releases.get(releases.size() - 1);
        }

        public String previous() {
            return releases.get(releases.size() - 2);
        }

        public List<String> listToDelete(int keepX) {
            if(releases.size() <= keepX) return Collections.emptyList();

            return releases.subList(0, releases.size() - keepX);
        }
    }

    public static final DynamicVariable<String>

    applicationsPath = strVar("applicationsPath", "System apps folder").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            return ctx.system.isNativeUnix() ? "/var/lib" : "c:";
        }
    }),

    logsPath = strVar("applicationsPath", "System apps folder").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            return ctx.system.isNativeUnix() ? "/var/log" : "c:";
        }
    }),

    applicationName = strVar("applicationName", "Your app name"),
    appLogsPath = joinPath("appLogsPath", logsPath, applicationName),
    sshUsername = strVar("sshUsername", ""),
    appUsername = eql("appUsername", sshUsername),
    sshPassword = strVar("sshPassword", ""),

    repositoryURI = strVar("repository", "Project VCS URI"),

    vcsType = enumConstant("vcsType", "Your VCS type", "svn"),
    vcsUserName = eql("vcsUserName", sshUsername),
    vcsPassword = eql("vcsPassword", sshPassword),

    deployTo = joinPath("deployTo", applicationsPath, applicationName).setDesc("Current release dir"),

    currentDirName = strVar("currentDirName", "Current release dir").defaultTo("current"),
    sharedDirName = strVar("sharedDirName", "").defaultTo("shared"),

    releasesDirName = strVar("releasesDirName", "").defaultTo("releases"),

    releaseName = strVar("releaseName", "I.e. 20140216").defaultTo(RELEASE_FORMATTER.print(new DateTime()) + ".GMT"),

    devEnvironment = enumConstant("devEnvironment", "Development environment", "dev", "test", "prod").defaultTo("prod"),

    revision = strVar("revision", "Get head revision").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            return vcs.apply(ctx).head();
        }
    }),

   realRevision = strVar("realRevision", "Update revision from vcs").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            final Vcs vcs = ctx.var(CapConstants.vcs);
            final CommandLine<BranchInfoResult> line = vcs.queryRevision(ctx.var(revision), Collections.<String, String>emptyMap());

            line.timeoutMs(20000);

            BranchInfoResult r = ctx.system.run(line, vcs.runCallback());

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

    getLatestReleasePath = strVar("getLatestReleasePath", "").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext input) {
            final Releases r = input.gvar(getReleases);

            if (r.releases.isEmpty()) return null;

            return input.system.joinPath(input.gvar(releasesPath), r.last());
        }
    }).memoize(true),

     getPreviousReleasePath = strVar("getPreviousReleasePath", "").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext input) {
            final Releases r = input.gvar(getReleases);

            if (r.releases.size() < 2) return null;

            return input.system.joinPath(input.gvar(releasesPath), r.previous());
        }
    }).memoize(true),

     getCurrentRevision = strVar("getCurrentRevision", "").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            return ctx.system.readString(ctx.joinPath(currentPath, "REVISION"), null);
        }
    }).memoize(true),

    getLatestReleaseRevision = strVar("getLatestReleaseRevision", "").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            return ctx.system.readString(ctx.joinPath(getLatestReleasePath, "REVISION"), null);
        }
    }).memoize(true),

     getPreviousReleaseRevision = strVar("getPreviousReleaseRevision", "").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            return ctx.system.readString(ctx.joinPath(getPreviousReleasePath, "REVISION"), null);
        }
    }).memoize(true);

    public static final DynamicVariable<Boolean>
        useSudo = bool("useSudo", "").defaultTo(true),
        productionDeployment = bool("productionDeployment", "").defaultTo(true),
        clean = eql("clean", productionDeployment),
        speedUpBuild = and("speedUpBuild", not("", productionDeployment), not("", clean)),
        scmAuthCache = dynamicNotSet("scmAuthCache", ""),
        scmPreferPrompt = dynamicNotSet("scmPreferPrompt", ""),
        isRemoteEnv = dynamic("isRemoteEnv", "", new Function<VarContext, Boolean>() {
            public Boolean apply(VarContext input) {
                return input.system.isRemote();
            }
        })
    ;

    public static final DynamicVariable<Integer>
        keepXReleases = CapConstants.<Integer>dynamic("keepXReleases", "").defaultTo(8);

    public static final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new Function<VarContext, Releases>() {
        public Releases apply(VarContext ctx) {
            return new Releases(ctx.system.ls(ctx.gvar(releasesPath)));
        }
    });


    public static final DynamicVariable<Vcs> vcs = new DynamicVariable<Vcs>("vcs", "VCS adapter").setDynamic(new Function<VarContext, Vcs>() {
        public Vcs apply(VarContext ctx) {
            final String scm = ctx.var(CapConstants.vcsType);

            if ("svn".equals(scm)) {
                return new SvnVcs(ctx);
            }

            throw new UnsupportedOperationException(scm + " is not yet supported");
        }
    });

    public static final DynamicVariable<BaseStrategy> newStrategy = dynamicNotSet("strategy", "Deployment strategy: how app files copied and built");

    public static <T> DynamicVariable<T> dynamicNotSet(final String name, String desc) {
        return dynamic(name, desc, new Function<VarContext, T>() {
            public T apply(@Nullable VarContext input) {
                throw new UnsupportedOperationException("you need to set the :" + name + " variable");
            }
        });
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc){
        return new DynamicVariable<T>(name, desc);
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc, Function<VarContext, T> function) {
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

    public static DynamicVariable<String> strVar(String name, String desc) {
        return new DynamicVariable<String>(name, desc);
    }

    public static DynamicVariable<Boolean> bool(String name, String desc) {
        return new DynamicVariable<Boolean>(name, desc);
    }
}
