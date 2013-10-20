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
import bear.plugins.DependencyInjection;
import bear.plugins.Plugin;
import bear.session.BearVariables;
import bear.session.DynamicVariable;
import bear.strategy.DeployStrategyTaskDef;
import bear.task.TaskDef;
import bear.vcs.BranchInfoResult;
import bear.vcs.VcsCLIPlugin;
import chaschev.lang.OpenBean;
import com.google.common.base.Optional;
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
        DependencyInjection.nameVars(this);
    }

    public final DynamicVariable<String>


    applicationsPath = strVar("System apps folder").setDynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.isNativeUnix() ? "/var/lib" : "c:";
        }
    }),

    bearPath = BearVariables.joinPath(applicationsPath, "bear"),

    logsPath = strVar("System apps folder").setDynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.isNativeUnix() ? "/var/log" : "c:";
        }
    }),

    taskName = strVar("A task to run").defaultTo("deploy");

    public final DynamicVariable<TaskDef> task = dynamic(new VarFun<TaskDef, SessionContext>() {
        @Override
        public TaskDef apply(SessionContext $) {
            return (TaskDef) OpenBean.getFieldValue(global.tasks, $.var(taskName));
        }
    });

    public final DynamicVariable<String>

    applicationName = strVar().setDesc("Your app name"),
        appLogsPath = BearVariables.joinPath("appLogsPath", logsPath, applicationName),
        sshUsername = dynamic(new VarFun<String, SessionContext>() {
            @Override
            public String apply(SessionContext $) {
                String username  = $.getProperty($.concat(sessionHostname, ".username"));

                if(username == null){
                    username = $.getProperty(var.name);
                }

                return username;
            }
        }),
        sshPassword = dynamic(new VarFun<String, SessionContext>() {
            @Override
            public String apply(SessionContext $) {
                String username  = $.getGlobal().getProperty($.concat(sessionHostname, ".password"));

                if(username == null){
                    username = $.getGlobal().getProperty(var.name);
                }

                return username;
            }
        }),
        appUsername = equalTo("appUsername", sshUsername),
        stage = strVar("Stage to deploy to"),
        repositoryURI = strVar("Project VCS URI"),
//        vcsType = enumConstant("vcsType", "Your VCS type", "svn", "git"),
        vcsUsername = equalTo("vcsUserName", sshUsername),
        vcsPassword = equalTo("vcsPassword", sshPassword),
        sessionHostname = strVar("internal variable containing the name of the current host"),
        sessionAddress = strVar("internal"),

    tempUserInput = strVar(""),

    deployScript = strVar("Script to use").defaultTo("CreateNewScript"),

    deployTo = BearVariables.joinPath("deployTo", applicationsPath, applicationName).setDesc("Current release dir"),

    currentDirName = strVar("Current release dir").defaultTo("current"),
        sharedDirName = strVar("").defaultTo("shared"),

    releasesDirName = strVar("").defaultTo("releases"),

    releaseName = strVar("I.e. 20140216").defaultTo(RELEASE_FORMATTER.print(new DateTime()) + ".GMT"),

    devEnvironment = enumConstant("devEnvironment", "Development environment", "dev", "test", "prod").defaultTo("prod"),

    revision = strVar("Get head revision").setDynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return vcs.apply($).head();
        }
    }),

    realRevision = strVar("Update revision from vcs").setDynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            final VcsCLIPlugin.Session vcsCLI = $.var(vcs);
            final Script<BranchInfoResult> line =
                vcsCLI.queryRevision($.var(revision), Collections.<String, String>emptyMap())
                .timeoutMs(20000);

            BranchInfoResult r = $.sys.run(line, vcsCLI.passwordCallback());

            return r.revision;
        }
    }),

    releasesPath = BearVariables.joinPath(deployTo, releasesDirName),
        currentPath = BearVariables.joinPath(deployTo, currentDirName),
        sharedPath = BearVariables.joinPath(bearPath, sharedDirName),
        projectSharedPath = BearVariables.joinPath(deployTo, sharedDirName),

    releasePath = BearVariables.joinPath(releasesPath, releaseName),

    vcsCheckoutPath = BearVariables.joinPath(projectSharedPath, "vcs"),

    vcsBranchName = dynamic("Relative path of the branch to use"),

    vcsBranchLocalPath = BearVariables.joinPath(vcsCheckoutPath, vcsBranchName),

    vcsBranchURI = BearVariables.joinPath(repositoryURI, vcsBranchName),

    getLatestReleasePath = dynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            final Releases r = $.var(getReleases);

            if (r.releases.isEmpty()) return null;

            return $.sys.joinPath($.var(releasesPath), r.last());
        }
    }).memoize(true),

    getPreviousReleasePath = dynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            final Releases r = $.var(getReleases);

            if (r.releases.size() < 1) return null;

            return $.sys.joinPath($.var(releasesPath), r.previous());
        }
    }).memoize(true),

    getCurrentRevision = dynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.readString($.joinPath(currentPath, "REVISION"), null);
        }
    }).memoize(true),

    getLatestReleaseRevision = strVar("").setDynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.readString($.joinPath(getLatestReleasePath, "REVISION"), null);
        }
    }).memoize(true),

    getPreviousReleaseRevision = strVar("").setDynamic(new VarFun<String, SessionContext>() {
        public String apply(SessionContext $) {
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
        isRemoteEnv = dynamic(new VarFun<Boolean, SessionContext>() {
            public Boolean apply(SessionContext $) {
                return $.sys.isRemote();
            }
        }),
        isNativeUnix = dynamic(new VarFun<Boolean, SessionContext>() {
            public Boolean apply(SessionContext $) {
                return $.sys.isNativeUnix();
            }
        }),
        isUnix = dynamic(new VarFun<Boolean, SessionContext>() {
            public Boolean apply(SessionContext $) {
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

    public final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new VarFun<Releases, SessionContext>() {
        public Releases apply(SessionContext $) {
            return new Releases($.sys.ls($.var(releasesPath)));
        }
    });

    public final DynamicVariable<Stages> stages = new DynamicVariable<Stages>("List of stages. Stage is collection of servers with roles and auth defined for each of the server.");
    public final DynamicVariable<Stage> getStage = dynamic(new VarFun<Stage, SessionContext>() {
        public Stage apply(SessionContext $) {
            final String stageName = $.var(Bear.this.stage);
            final Optional<Stage> optional = Iterables.tryFind($.var(stages).stages, new Predicate<Stage>() {
                public boolean apply(Stage s) {
                    return s.name.equals(stageName);
                }
            });

            if(!optional.isPresent()){
                throw new RuntimeException("stage not found: '" + stageName + "'");
            }

            Stage stage = optional.get();
            stage.global = global;

            return stage;
        }
    });

    public final DynamicVariable<VcsCLIPlugin.Session> vcs = new DynamicVariable<VcsCLIPlugin.Session>("vcs", "VCS adapter").setDynamic(new VarFun<VcsCLIPlugin.Session, SessionContext>() {
        public VcsCLIPlugin.Session apply(SessionContext $) {
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
        globalPropertiesFile = dynamic(new VarFun<File, SessionContext>() {
            public File apply(SessionContext $) {
                return new File($.var(scriptsDir), "global.properties");
            }
        });

    public final DynamicVariable<DeployStrategyTaskDef> getStrategy = dynamic(DeployStrategyTaskDef.class).setDesc("Deployment strategy: how app files copied and built").memoize(true);


}
