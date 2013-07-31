package cap4j;

import cap4j.scm.BaseScm;
import cap4j.scm.SvnScm;
import cap4j.session.DynamicVariable;
import cap4j.strategy.BaseStrategy;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static cap4j.GlobalContext.local;
import static cap4j.session.VariableUtils.joinPath;

/**
 * User: ACHASCHEV
 * Date: 7/27/13
 */
public class CapConstants {
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
    }

    public static final DynamicVariable<String>

        applicationsPath = strVar("applicationsPath", "System apps folder").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext input) {
            return SystemUtils.IS_OS_WINDOWS ? "c:" : "/var/lib";
        }
    }),

    applicationName = strVar("applicationName", "Your app name"),

    repositoryURI = strVar("repository", "Project VCS URI"),

    scm = enumConstant("Your VCS type", "svn"),

    deployTo = joinPath("deployTo", applicationsPath, applicationName).setDesc("Current release dir"),

    currentDirName = strVar("currentDirName", "Current release dir").defaultTo("current"),

    releasesDirName = strVar("releasesDirName", "").defaultTo("releases"),

    releaseName = strVar("releaseName", "I.e. 20140216").defaultTo(new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date())),

    devEnvironment = enumConstant("Development environment", "dev", "test", "prod").defaultTo("prod"),

    revision = strVar("revision", "Get head revision").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            return vcs.apply(ctx).head();
        }
    }),

   realRevision = strVar("realRevision", "Update revision from vcs").setDynamic(new Function<VarContext, String>() {
        public String apply(VarContext ctx) {
            BaseScm.StringResult r = local().runVCS(ctx.gvar(vcs).queryRevision(ctx.varS(revision), Collections.<String, String>emptyMap()));

            return r.value;
        }
    }),

    releasesPath = joinPath("releasesPath", deployTo, releasesDirName),
    currentPath = joinPath("currentPath", deployTo, currentDirName),
    releasePath = joinPath("releasesPath", releasesPath, releaseName),

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

    DynamicVariable<Boolean> useSudo = bool("useSudo", "").defaultTo(true);

    public static final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new Function<VarContext, Releases>() {
        public Releases apply(VarContext ctx) {
            return new Releases(ctx.system.ls(ctx.gvar(releasesPath)));
        }
    });


    public static final DynamicVariable<BaseScm> vcs = new DynamicVariable<BaseScm>("vcs", "VCS adapter").setDynamic(new Function<VarContext, BaseScm>() {
        public BaseScm apply(VarContext ctx) {
            final String scm = ctx.varS(CapConstants.scm);

            if ("svn".equals(scm)) {
                return new SvnScm();
            }

            throw new UnsupportedOperationException(scm + " is not yet supported");
        }
    }).memoize(true);

    public static final DynamicVariable<BaseStrategy> newStrategy = dynamicNotSet("strategy", "Deployment strategy: how app files copied and built");

    public static <T> DynamicVariable<T> dynamicNotSet(final String name, String desc) {
        return dynamic(name, desc, new Function<VarContext, T>() {
            public T apply(@Nullable VarContext input) {
                throw new UnsupportedOperationException("you need to set the :" + name + " variable");
            }
        });
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc, Function<VarContext, T> function) {
        return new DynamicVariable<T>(name, desc).setDynamic(function);
    }


    private static DynamicVariable<String> enumConstant(final String desc, final String... options) {
        return new DynamicVariable<String>("devEnv", desc) {
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
