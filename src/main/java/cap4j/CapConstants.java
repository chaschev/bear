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

        applicationsPath = strVar("applicationsPath", "System apps folder").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context input) {
            return SystemUtils.IS_OS_WINDOWS ? "c:" : "/var/lib";
        }
    });

    public static final DynamicVariable<String> applicationName = strVar("applicationName", "Your app name");

    public static final DynamicVariable<String> repositoryURI = strVar("repository", "Project VCS URI");

    public static final DynamicVariable<String> scm = enumConstant("Your VCS type", "svn");

    public static final DynamicVariable<String> deployTo = strVar("deployTo", "Current release dir")
        .setDynamic(new Function<Variables.Context, String>() {
            public String apply(Variables.Context ctx) {
                return ctx.system.joinPath(ctx.varS(applicationsPath), ctx.varS(applicationName));
            }
        });

    public static final DynamicVariable<String> currentDirName = strVar("currentDirName", "Current release dir").defaultTo("current");
    public static final DynamicVariable<String> releasesDirName = strVar("releasesDirName", "").defaultTo("releases");
    public static final DynamicVariable<String> releaseName = strVar("releaseName", "I.e. 20140216").defaultTo(new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date()));

    public static final DynamicVariable<String> devEnvironment = enumConstant("Development environment", "dev", "test", "prod").defaultTo("prod");

    public static final DynamicVariable<String> revision = strVar("revision", "Get head revision").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            return vcs.apply(ctx).head();
        }
    });

    public static final DynamicVariable<String> realRevision = strVar("realRevision", "Update revision from vcs").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            BaseScm.StringResult r = ctx.local.runVCS(ctx.gvar(vcs).queryRevision(ctx.varS(revision), Collections.<String, String>emptyMap()));

            return r.value;
        }
    });

    public static final DynamicVariable<String> releasesPath = strVar("releasesPath", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            return ctx.system.joinPath(ctx.varS(deployTo), ctx.varS(releasesDirName));
        }
    });

    public static final DynamicVariable<String> currentPath = strVar("currentPath", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            return ctx.system.joinPath(ctx.varS(deployTo), ctx.varS(currentDirName));
        }
    });

    public static final DynamicVariable<String> releasePath = strVar("releasePath", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            return ctx.system.joinPath(ctx.varS(releasesPath), ctx.varS(releaseName));
        }
    });

    public static final DynamicVariable<String> getLatestReleasePath = strVar("getLatestReleasePath", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context input) {
            final Releases r = input.gvar(getReleases);

            if (r.releases.isEmpty()) return null;

            return input.system.joinPath(input.gvar(releasesPath), r.last());
        }
    }).memoize(true);

    public static final DynamicVariable<String> getPreviousReleasePath = strVar("getPreviousReleasePath", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context input) {
            final Releases r = input.gvar(getReleases);

            if (r.releases.size() < 2) return null;

            return input.system.joinPath(input.gvar(releasesPath), r.previous());
        }
    }).memoize(true);

    public static final DynamicVariable<String> getCurrentRevision = strVar("getCurrentRevision", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            return ctx.system.readString(ctx.joinPath(currentPath, "REVISION"), null);
        }
    }).memoize(true);

    public static final DynamicVariable<String> getLatestReleaseRevision = strVar("getLatestReleaseRevision", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            return ctx.system.readString(ctx.joinPath(getLatestReleasePath, "REVISION"), null);
        }
    }).memoize(true);

    public static final DynamicVariable<String> getPreviousReleaseRevision = strVar("getPreviousReleaseRevision", "").setDynamic(new Function<Variables.Context, String>() {
        public String apply(Variables.Context ctx) {
            return ctx.system.readString(ctx.joinPath(getPreviousReleasePath, "REVISION"), null);
        }
    }).memoize(true);

    DynamicVariable<Boolean> useSudo = bool("useSudo", "").defaultTo(true);

    public static final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new Function<Variables.Context, Releases>() {
        public Releases apply(Variables.Context ctx) {
            return new Releases(ctx.system.ls(ctx.gvar(releasesPath)));
        }
    });


    public static final DynamicVariable<BaseScm> vcs = new DynamicVariable<BaseScm>("vcs", "VCS adapter").setDynamic(new Function<Variables.Context, BaseScm>() {
        public BaseScm apply(Variables.Context ctx) {
            final String scm = ctx.varS(CapConstants.scm);

            if ("svn".equals(scm)) {
                return new SvnScm();
            }

            throw new UnsupportedOperationException(scm + " is not yet supported");
        }
    }).memoize(true);

    public static final DynamicVariable<BaseStrategy> newStrategy = dynamicNotSet("strategy", "Deployment strategy: how app files copied and built");

    public static <T> DynamicVariable<T> dynamicNotSet(final String name, String desc){
        return dynamic(name, desc, new Function<Variables.Context, T>() {
            public T apply(@Nullable Variables.Context input) {
                throw new UnsupportedOperationException("you need to set the :" + name + " variable");
            }
        });
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc, Function<Variables.Context, T> function){
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
    };

    public static DynamicVariable<String> strVar(String name, String desc) {
        return new DynamicVariable<String>(name, desc);
    }

    public static DynamicVariable<Boolean> bool(String name, String desc) {
        return new DynamicVariable<Boolean>(name, desc);
    }
}
