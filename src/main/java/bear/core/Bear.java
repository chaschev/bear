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

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.context.VarFun;
import bear.plugins.Plugin;
import bear.session.Address;
import bear.session.BearVariables;
import bear.session.DynamicVariable;
import bear.strategy.DeployStrategyTaskDef;
import bear.task.TaskDef;
import bear.vcs.BranchInfoResult;
import bear.vcs.VCSSession;
import bear.vcs.VcsCLIPlugin;
import chaschev.lang.Functions2;
import chaschev.lang.OpenBean;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.util.*;

import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Bear extends BearApp<GlobalContext> {
    public static final DateTimeZone GMT = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
    public static final DateTimeFormatter RELEASE_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd.HHmmss").withZone(GMT);

    public Bear() {

    }

    public final DynamicVariable<String>


    applicationsPath = strVar("System apps folder").setDynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.isNativeUnix() ? "/var/lib" : "c:";
        }
    }),

    bearPath = BearVariables.joinPath(applicationsPath, "bear"),

    logsPath = strVar("System apps folder").setDynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.isNativeUnix() ? "/var/log" : "c:";
        }
    }),

    taskName = strVar("A task to run").defaultTo("deploy");

    public final DynamicVariable<TaskDef> task = dynamic(new Fun<TaskDef, SessionContext>() {
        @Override
        public TaskDef apply(SessionContext $) {
            return (TaskDef) OpenBean.getFieldValue(global.tasks, $.var(taskName));
        }
    });

    public final DynamicVariable<String>

        appLogsPath = BearVariables.joinPath(logsPath, name),
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
                String username = $.getGlobal().getProperty($.concat(sessionHostname, ".password"));

                if (username == null) {
                    username = $.getGlobal().getProperty(var.name);
                }

                return username;
            }
        }),
        appUsername = equalTo(sshUsername),
        stage = strVar("Stage to deploy to"),
        repositoryURI = strVar("Project VCS URI"),
//        vcsType = enumConstant("vcsType", "Your VCS type", "svn", "git"),
        vcsUsername = equalTo(sshUsername),
        vcsPassword = equalTo(sshPassword),
        sessionHostname = strVar("internal variable containing the name of the current host"),
        sessionAddress = strVar("internal"),

    tempUserInput = strVar(""),

    deployScript = strVar("Script to use").defaultTo("CreateNewScript"),

    deployTo = BearVariables.joinPath(applicationsPath, name).desc("Current release dir"),

    currentDirName = strVar("Current release dir").defaultTo("current"),
        sharedDirName = strVar("").defaultTo("shared"),

    releasesDirName = strVar("").defaultTo("releases"),

    releaseName = strVar("I.e. 20140216").defaultTo(RELEASE_FORMATTER.print(new DateTime()) + ".GMT"),

    devEnvironment = enumConstant("devEnvironment", "Development environment", "dev", "test", "prod").defaultTo("prod"),

    revision = strVar("Get head revision").setDynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return vcs.apply($).head();
        }
    }),

    realRevision = strVar("Update revision from vcs").setDynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            final VCSSession vcsCLI = $.var(vcs);

            BranchInfoResult r = vcsCLI.queryRevision($.var(revision))
                .timeoutSec(20).run();

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

    getLatestReleasePath = dynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            final Releases r = $.var(getReleases);

            if (r.releases.isEmpty()) return null;

            return $.sys.joinPath($.var(releasesPath), r.last());
        }
    }).memoize(true),

    getPreviousReleasePath = dynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            final Releases r = $.var(getReleases);

            if (r.releases.size() < 1) return null;

            return $.sys.joinPath($.var(releasesPath), r.previous());
        }
    }).memoize(true),

    getCurrentRevision = dynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.readString($.joinPath(currentPath, "REVISION"), null);
        }
    }).memoize(true),

    getLatestReleaseRevision = strVar("").setDynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.readString($.joinPath(getLatestReleasePath, "REVISION"), null);
        }
    }).memoize(true),

    getPreviousReleaseRevision = strVar("").setDynamic(new Fun<String, SessionContext>() {
        public String apply(SessionContext $) {
            return $.sys.readString($.joinPath(getPreviousReleasePath, "REVISION"), null);
        }
    }).memoize(true);

    public final DynamicVariable<Boolean>
        useSudo = bool("").defaultTo(true),
        productionDeployment = bool("").defaultTo(true),
        clean = equalTo(productionDeployment),
        speedUpBuild = and(not(productionDeployment), not(clean)),
        vcsAuthCache = dynamic(""),
        vcsPreferPrompt = dynamic(""),
        isRemoteEnv = dynamic(new Fun<Boolean, SessionContext>() {
            public Boolean apply(SessionContext $) {
                return $.sys.isRemote();
            }
        }),
        isNativeUnix = dynamic(new Fun<Boolean, SessionContext>() {
            public Boolean apply(SessionContext $) {
                return $.sys.isNativeUnix();
            }
        }),
        isUnix = dynamic(new Fun<Boolean, SessionContext>() {
            public Boolean apply(SessionContext $) {
                return $.sys.isUnix();
            }
        }),
        internalInteractiveRun = newVar(false),
        interactiveRun = equalTo(internalInteractiveRun).desc("use it to override interactiveRun"),
        checkDependencies = not(interactiveRun),
        verifyPlugins = equalTo(checkDependencies),
        autoInstallPlugins = newVar(false),
        verbose = newVar(false)
    ;

    public final DynamicVariable<Integer>
        keepXReleases = newVar(5),
        taskTimeoutSec = newVar(15 * 60)
    ;

    public final DynamicVariable<Releases> getReleases = new DynamicVariable<Releases>("getReleases", "").setDynamic(new Fun<Releases, SessionContext>() {
        public Releases apply(SessionContext $) {
            return new Releases($.sys.ls($.var(releasesPath)));
        }
    });

    public final DynamicVariable<Stages> stages = new DynamicVariable<Stages>("List of stages. Stage is collection of servers with roles and auth defined for each of the server.");
    public final DynamicVariable<Stage> getStage = dynamic(new Fun<Stage, GlobalContext>() {
        public Stage apply(GlobalContext $) {
            return findStage($);
        }
    });

    public final DynamicVariable<List<String>> activeHosts = undefined();
    public final DynamicVariable<List<String>> activeRoles = undefined();

    public final DynamicVariable<Function<Stage, Collection<Address>>> addressesForStage = dynamic(new Fun<Function<Stage, Collection<Address>>, AbstractContext>() {
        @Override
        public Function<Stage, Collection<Address>> apply(final AbstractContext $) {
            return new Function<Stage, Collection<Address>>() {
                public Collection<Address> apply(Stage stage) {
                    List<String> hosts = new ArrayList<String>();

                    boolean hostsDefined = $.isDefined(activeHosts);

                    if(hostsDefined){
                        hosts.addAll(stage.validate($(activeHosts)));
                    }

                    boolean rolesDefined = $.isDefined(activeRoles);

                    if(rolesDefined){
                        hosts.addAll(Collections2.transform(stage.getHostsForRoles($(activeRoles)), Functions2.<Address, String>method("getName")));
                    }

                    if(hosts.isEmpty() && !(rolesDefined || hostsDefined)){
                        return stage.getAddresses();
                    }

                    return stage.mapNamesToAddresses(hosts);
                }
            };
        }
    });

    public Stage findStage(GlobalContext $) {
        final String stageName = $.var(this.stage);
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

    public final DynamicVariable<VCSSession> vcs = new DynamicVariable<VCSSession>("vcs", "VCS adapter").setDynamic(new Fun<VCSSession, SessionContext>() {
        public VCSSession apply(SessionContext $) {
            Class<? extends VcsCLIPlugin> vcsCLI = null;

            for (Class<? extends Plugin> aClass : global.getPluginClasses()) {
                if(VcsCLIPlugin.class.isAssignableFrom(aClass)){
                    vcsCLI = (Class<? extends VcsCLIPlugin>) aClass;
                }
            }

            Preconditions.checkNotNull(vcsCLI, "add a VCS plugin!");

            return (VCSSession) global.newPluginSession(vcsCLI, $, $.getCurrentTask());
        }
    });

    public final DynamicVariable<File>
        scriptsDir = newVar(new File(".bear")),
        globalPropertiesFile = dynamic(new Fun<File, SessionContext>() {
            public File apply(SessionContext $) {
                return new File($.var(scriptsDir), "global.properties");
            }
        });

    public final DynamicVariable<DeployStrategyTaskDef> getStrategy = dynamic(DeployStrategyTaskDef.class).desc("Deployment strategy: how app files copied and built").memoize(true);


}
