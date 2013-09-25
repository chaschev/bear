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

package cap4j.core;

import cap4j.plugins.Plugin;
import cap4j.scm.BranchInfoResult;
import cap4j.cli.CommandLine;
import cap4j.scm.SvnVcsCLI;
import cap4j.scm.VcsCLI;
import cap4j.session.DynamicVariable;
import cap4j.strategy.BaseStrategy;
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
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Cap {
    public static final DateTimeZone GMT = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
    public static final DateTimeFormatter RELEASE_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd.HHmmss").withZone(GMT);
    public final GlobalContext global;

    public Cap(GlobalContext global) {
        this.global = global;
        Plugin.nameVars(this);
    }

    public final DynamicVariable<String>

        applicationsPath = strVar("System apps folder").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.system.isNativeUnix() ? "/var/lib" : "c:";
        }
    }),

    logsPath = strVar("System apps folder").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.system.isNativeUnix() ? "/var/log" : "c:";
        }
    }),

    task = strVar("A task to run").defaultTo("deploy"),

    applicationName = strVar().setDesc("Your app name"),
        appLogsPath = joinPath("appLogsPath", logsPath, applicationName),
        sshUsername = strVar(""),
        appUsername = eql("appUsername", sshUsername),
        sshPassword = dynamic(new VarFun<String>() {
            public String apply() {
                return global.getProperty($.var(sessionHostname) + ".password");
            }
        }),
        stage = strVar("Stage to deploy to"),
        repositoryURI = strVar("Project VCS URI"),
        vcsType = enumConstant("vcsType", "Your VCS type", "svn"),
        vcsUsername = eql("vcsUserName", sshUsername),
        vcsPassword = eql("vcsPassword", sshPassword),
        sessionHostname = strVar("internal variable containing the name of the current session"),

    tempUserInput = strVar(""),

    deployScript = strVar("Script to use").defaultTo("CreateNewScript"),

    deployTo = joinPath("deployTo", applicationsPath, applicationName).setDesc("Current release dir"),

    currentDirName = strVar("Current release dir").defaultTo("current"),
        sharedDirName = strVar("").defaultTo("shared"),

    releasesDirName = strVar("").defaultTo("releases"),

    releaseName = strVar("I.e. 20140216").defaultTo(RELEASE_FORMATTER.print(new DateTime()) + ".GMT"),

    devEnvironment = enumConstant("devEnvironment", "Development environment", "dev", "test", "prod").defaultTo("prod"),

    revision = strVar("Get head revision").setDynamic(new VarFun<String>() {
        public String apply() {
            return vcs.apply($).head();
        }
    }),

    realRevision = strVar("Update revision from vcs").setDynamic(new VarFun<String>() {
        public String apply() {
            final VcsCLI vcsCLI = $.var(vcs);
            final CommandLine<BranchInfoResult> line = vcsCLI.queryRevision($.var(revision), Collections.<String, String>emptyMap());

            line.timeoutMs(20000);

            BranchInfoResult r = $.system.run(line, vcsCLI.passwordCallback());

            return r.revision;
        }
    }),

    releasesPath = joinPath("releasesPath", deployTo, releasesDirName),
        currentPath = joinPath("currentPath", deployTo, currentDirName),
        sharedPath = joinPath("sharedPath", deployTo, sharedDirName),

    releasePath = joinPath("releasesPath", releasesPath, releaseName),

    vcsCheckoutPath = joinPath("vcsCheckoutPath", sharedPath, "vcs"),
        vcsBranchName = strVar("").defaultTo("trunk"),
        vcsBranchLocalPath = joinPath("vcsBranchLocalPath", vcsCheckoutPath, vcsBranchName),
        vcsBranchURI = joinPath("vcsProjectURI", repositoryURI, vcsBranchName),

    getLatestReleasePath = strVar("").setDynamic(new VarFun<String>() {
        public String apply() {
            final Releases r = $.var(getReleases);

            if (r.releases.isEmpty()) return null;

            return $.system.joinPath($.var(releasesPath), r.last());
        }
    }).memoize(true),

    getPreviousReleasePath = strVar("").setDynamic(new VarFun<String>() {
        public String apply() {
            final Releases r = $.var(getReleases);

            if (r.releases.size() < 2) return null;

            return $.system.joinPath($.var(releasesPath), r.previous());
        }
    }).memoize(true),

    getCurrentRevision = strVar("").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.system.readString($.joinPath(currentPath, "REVISION"), null);
        }
    }).memoize(true),

    getLatestReleaseRevision = strVar("").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.system.readString($.joinPath(getLatestReleasePath, "REVISION"), null);
        }
    }).memoize(true),

    getPreviousReleaseRevision = strVar("").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.system.readString($.joinPath(getPreviousReleasePath, "REVISION"), null);
        }
    }).memoize(true);

    public final DynamicVariable<Boolean>
        useSudo = bool("").defaultTo(true),
        productionDeployment = bool("").defaultTo(true),
        clean = eql("clean", productionDeployment),
        speedUpBuild = and(not("", productionDeployment), not("", clean)),
        scmAuthCache = dynamicNotSet("scmAuthCache", ""),
        scmPreferPrompt = dynamicNotSet("scmPreferPrompt", ""),
        isRemoteEnv = dynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return $.system.isRemote();
            }
        }),
        isNativeUnix = dynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return $.system.isNativeUnix();
            }
        }),
        isUnix = dynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return $.system.isUnix();
            }
        }),
        verifyPlugins = newVar(true),
        autoSetupPlugins = newVar(false);

    public static final DynamicVariable<Integer>
        keepXReleases = newVar(5);

    public final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new VarFun<Releases>() {
        public Releases apply() {
            return new Releases($.system.ls($.var(releasesPath)));
        }
    });

    public final DynamicVariable<Stages> stages = new DynamicVariable<Stages>("stages", "List of stages. Stage is collection of servers with roles and auth defined for each of the server.");
    public final DynamicVariable<Stage> getStage = dynamic("getStage", "", new VarFun<Stage>() {
        public Stage apply() {
            final String stageName = $.var(Cap.this.stage);
            final Stage stage = Iterables.find($.var(stages).stages, new Predicate<Stage>() {
                public boolean apply(Stage s) {
                    return s.name.equals(stageName);
                }
            });

            stage.global = global;

            return stage;
        }
    });

    public final DynamicVariable<VcsCLI> vcs = new DynamicVariable<VcsCLI>("vcs", "VCS adapter").setDynamic(new VarFun<VcsCLI>() {
        public VcsCLI apply() {
            final String scm = $.var(vcsType);

            if ("svn".equals(scm)) {
                return new SvnVcsCLI($, global);
            }

            throw new UnsupportedOperationException(scm + " is not yet supported");
        }
    });

    public final DynamicVariable<File>
        scriptsDir = newVar(new File(".cap")),
        settingsFile = dynamic(new VarFun<File>() {
            public File apply() {
                return new File($.var(scriptsDir), "settings.properties");
            }
        });

    public static final DynamicVariable<BaseStrategy> newStrategy = dynamicNotSet("strategy", "Deployment strategy: how app files copied and built");

    public static <T> DynamicVariable<T> dynamicNotSet(String desc) {
        return dynamicNotSet(null, desc);
    }

    public static <T> DynamicVariable<T> dynamicNotSet(final String name, String desc) {
        return dynamic(name, desc, new VarFun<T>() {
            public T apply() {
                throw new UnsupportedOperationException("you need to set the :" + var.name + "'s name!");
            }
        });
    }

    public static <T> DynamicVariable<T> newVar(T _default) {
        return new DynamicVariable<T>("").defaultTo(_default);
    }

    public static <T> DynamicVariable<T> dynamic(VarFun<T> function) {
        return dynamic(null, "", function);
    }

    public static <T> DynamicVariable<T> dynamic(String desc) {
        return dynamic(null, desc);
    }

    static <T> DynamicVariable<T> dynamic(String name, String desc) {
        return new DynamicVariable<T>(name, desc);
    }

    public static <T> DynamicVariable<T> dynamic(String desc, VarFun<T> function) {
        return new DynamicVariable<T>((String) null, desc).setDynamic(function);
    }

    public static <T> DynamicVariable<T> dynamic(String name, String desc, VarFun<T> function) {
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
        return strVar("");
    }

    public static DynamicVariable<String> strVar(String desc) {
        return new DynamicVariable<String>((String) null, desc);
    }

    public static DynamicVariable<Boolean> bool() {
        return new DynamicVariable<Boolean>(null);
    }

    public static DynamicVariable<Boolean> bool(String desc) {
        return new DynamicVariable<Boolean>(desc);
    }
}
