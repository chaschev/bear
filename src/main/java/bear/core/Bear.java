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

package bear.core;

import bear.cli.Script;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.strategy.DeployStrategy;
import bear.task.TaskDef;
import bear.vcs.BranchInfoResult;
import bear.vcs.VcsCLIPlugin;
import com.chaschev.chutils.util.OpenBean2;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.util.Collections;
import java.util.TimeZone;

import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Bear {
    public static final DateTimeZone GMT = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
    public static final DateTimeFormatter RELEASE_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd.HHmmss").withZone(GMT);
    public final GlobalContext global;

    public Bear(GlobalContext global) {
        this.global = global;
        Plugin.nameVars(this);
    }

    public final DynamicVariable<String>


    applicationsPath = strVar("System apps folder").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.sys.isNativeUnix() ? "/var/lib" : "c:";
        }
    }),

    bearPath = joinPath(applicationsPath, "bear"),

    logsPath = strVar("System apps folder").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.sys.isNativeUnix() ? "/var/log" : "c:";
        }
    }),

    taskName = strVar("A task to run").defaultTo("deploy");

    public final DynamicVariable<TaskDef> task = dynamic(new VarFun<TaskDef>() {
        @Override
        public TaskDef apply() {
            return (TaskDef) OpenBean2.getFieldValue2(global.tasks, $(taskName));
        }
    });

    public final DynamicVariable<String>

    applicationName = strVar().setDesc("Your app name"),
        appLogsPath = joinPath("appLogsPath", logsPath, applicationName),
        sshUsername = strVar(""),
        appUsername = equalTo("appUsername", sshUsername),
        sshPassword = dynamic(new VarFun<String>() {
            public String apply() {
                return global.getProperty($(sessionHostname) + ".password");
            }
        }),
        stage = strVar("Stage to deploy to"),
        repositoryURI = strVar("Project VCS URI"),
//        vcsType = enumConstant("vcsType", "Your VCS type", "svn", "git"),
        vcsUsername = equalTo("vcsUserName", sshUsername),
        vcsPassword = equalTo("vcsPassword", sshPassword),
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
            final VcsCLIPlugin.Session vcsCLI = $(vcs);
            final Script<BranchInfoResult> line =
                vcsCLI.queryRevision($(revision), Collections.<String, String>emptyMap())
                .timeoutMs(20000);

            BranchInfoResult r = $.sys.run(line, vcsCLI.passwordCallback());

            return r.revision;
        }
    }),

    releasesPath = joinPath(deployTo, releasesDirName),
        currentPath = joinPath(deployTo, currentDirName),
        sharedPath = joinPath(bearPath, sharedDirName),
        projectSharedPath = joinPath(deployTo, sharedDirName),

    releasePath = joinPath(releasesPath, releaseName),

    vcsCheckoutPath = joinPath(projectSharedPath, "vcs"),

    vcsBranchName = dynamic("Relative path of the branch to use"),

    vcsBranchLocalPath = joinPath( vcsCheckoutPath, vcsBranchName),

    vcsBranchURI = joinPath(repositoryURI, vcsBranchName),

    getLatestReleasePath = dynamic(new VarFun<String>() {
        public String apply() {
            final Releases r = $(getReleases);

            if (r.releases.isEmpty()) return null;

            return $.sys.joinPath($(releasesPath), r.last());
        }
    }).memoize(true),

    getPreviousReleasePath = dynamic(new VarFun<String>() {
        public String apply() {
            final Releases r = $(getReleases);

            if (r.releases.size() < 1) return null;

            return $.sys.joinPath($(releasesPath), r.previous());
        }
    }).memoize(true),

    getCurrentRevision = dynamic(new VarFun<String>() {
        public String apply() {
            return $.sys.readString($.joinPath(currentPath, "REVISION"), null);
        }
    }).memoize(true),

    getLatestReleaseRevision = strVar("").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.sys.readString($.joinPath(getLatestReleasePath, "REVISION"), null);
        }
    }).memoize(true),

    getPreviousReleaseRevision = strVar("").setDynamic(new VarFun<String>() {
        public String apply() {
            return $.sys.readString($.joinPath(getPreviousReleasePath, "REVISION"), null);
        }
    }).memoize(true);

    public final DynamicVariable<Boolean>
        useSudo = bool("").defaultTo(true),
        productionDeployment = bool("").defaultTo(true),
        clean = equalTo(productionDeployment),
        speedUpBuild = and(not("", productionDeployment), not("", clean)),
        vcsAuthCache = dynamic(""),
        vcsPreferPrompt = dynamic(""),
        isRemoteEnv = dynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return $.sys.isRemote();
            }
        }),
        isNativeUnix = dynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return $.sys.isNativeUnix();
            }
        }),
        isUnix = dynamic(new VarFun<Boolean>() {
            public Boolean apply() {
                return $.sys.isUnix();
            }
        }),
        checkDependencies = newVar(true),
        verifyPlugins = equalTo(checkDependencies),
        autoInstallPlugins = newVar(false),
        verbose = newVar(false)
    ;

    public final DynamicVariable<Integer>
        keepXReleases = newVar(5),
        taskTimeoutSec = newVar(15 * 60)
    ;

    public final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new VarFun<Releases>() {
        public Releases apply() {
            return new Releases($.sys.ls($(releasesPath)));
        }
    });

    public final DynamicVariable<Stages> stages = new DynamicVariable<Stages>("List of stages. Stage is collection of servers with roles and auth defined for each of the server.");
    public final DynamicVariable<Stage> getStage = dynamic(new VarFun<Stage>() {
        public Stage apply() {
            final String stageName = $(Bear.this.stage);
            final Stage stage = Iterables.find($(stages).stages, new Predicate<Stage>() {
                public boolean apply(Stage s) {
                    return s.name.equals(stageName);
                }
            });

            stage.global = global;

            return stage;
        }
    });

    public final DynamicVariable<VcsCLIPlugin.Session> vcs = new DynamicVariable<VcsCLIPlugin.Session>("vcs", "VCS adapter").setDynamic(new VarFun<VcsCLIPlugin.Session>() {
        public VcsCLIPlugin.Session apply() {
            Class<? extends VcsCLIPlugin> vcsCLI = null;

            for (Class<? extends Plugin> aClass : global.getPluginClasses()) {
                if(VcsCLIPlugin.class.isAssignableFrom(aClass)){
                    vcsCLI = (Class<? extends VcsCLIPlugin>) aClass;
                }
            }

            Preconditions.checkNotNull(vcsCLI, "add a VCS plugin!");

            return (VcsCLIPlugin.Session) global.newPluginSession(vcsCLI, $, $.getCurrentTask());
        }
    });

    public final DynamicVariable<File>
        scriptsDir = newVar(new File(".bear")),
        settingsFile = dynamic(new VarFun<File>() {
            public File apply() {
                return new File($(scriptsDir), "settings.properties");
            }
        });

    public final DynamicVariable<DeployStrategy> getStrategy = dynamic(DeployStrategy.class).setDesc("Deployment strategy: how app files copied and built").memoize(true);

}
