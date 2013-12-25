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
import bear.session.Address;
import bear.session.BearVariables;
import bear.session.DynamicVariable;
import bear.task.TaskDef;
import bear.vcs.BranchInfo;
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
import org.apache.commons.lang3.RandomStringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static bear.session.BearVariables.joinPath;
import static bear.session.Variables.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Bear extends BearApp<GlobalContext> {


    public Bear() {

    }

    public final DynamicVariable<Boolean>
        isNativeUnix = dynamic(new Fun<SessionContext, Boolean>() {
        public Boolean apply(SessionContext $) {
            return $.sys.isNativeUnix();
        }
    }),
        isUnix = dynamic(new Fun<SessionContext, Boolean>() {
            public Boolean apply(SessionContext $) {
                return $.sys.isUnix();
            }
        });

    public final DynamicVariable<String>


    applicationsPath = condition(isNativeUnix, newVar("/var/lib").temp(), newVar("c:").temp()),

    bearPath = joinPath(applicationsPath, "bear"),

    sysLogsPath = condition(isNativeUnix, newVar("/var/log").temp(), (DynamicVariable) undefined()),

    taskName = strVar("A task to run").defaultTo("deploy");

    public final DynamicVariable<TaskDef> task = dynamic(new Fun<SessionContext, TaskDef>() {
        @Override
        public TaskDef apply(SessionContext $) {
            return (TaskDef) OpenBean.getFieldValue(global.tasks, $.var(taskName));
        }
    });

    public final DynamicVariable<String>

        sshUsername = dynamic(new VarFun<String, SessionContext>() {
            @Override
            public String apply(SessionContext $) {
                String username  = $.getProperty($.concat(sessionHostname, ".username"));

                if(username == null){
                    username = $.getProperty(var.name());
                }

                return username;
            }
        }),
        sshPassword = dynamic(new VarFun<String, SessionContext>() {
            @Override
            public String apply(SessionContext $) {
                String username = $.getGlobal().getProperty($.concat(sessionHostname, ".password"));

                if (username == null) {
                    username = $.getGlobal().getProperty(var.name());
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

    applicationPath = joinPath(applicationsPath, name),
    appLogsPath = condition(isNativeUnix, joinPath(sysLogsPath, name), concat(applicationPath, "log")),

    sharedDirName = strVar("").defaultTo("shared"),

    devEnvironment = enumConstant("devEnvironment", "Development environment", "dev", "test", "prod").defaultTo("prod"),

    revision = strVar("Get head revision").setDynamic(new Fun<SessionContext, String>() {
        public String apply(SessionContext $) {
            return vcs.apply($).head();
        }
    }),

    realRevision = strVar("Update revision from vcs").setDynamic(new Fun<SessionContext, String>() {
        public String apply(SessionContext $) {
            final VCSSession vcsCLI = $.var(vcs);

            BranchInfo r = vcsCLI.queryRevision($.var(revision)).run();

            return r.revision;
        }
    }),


        sharedPath = joinPath(bearPath, sharedDirName),
        projectSharedPath = joinPath(applicationPath, sharedDirName),
        tempDirPath = joinPath(applicationPath, "temp"),
        toolsSharedDirPath = joinPath(sharedPath, "tools"),
        downloadDirPath = BearVariables.joinPath(sharedPath, "downloads"),
        toolsInstallDirPath = newVar("/var/lib/bear/tools"),

    vcsCheckoutPath = joinPath(projectSharedPath, "vcs"),

    vcsBranchName = dynamic("Relative path of the branch to use"),
    vcsTag = undefined("tag name, HEAD revision is used by default"),

    vcsBranchLocalPath = joinPath(vcsCheckoutPath, vcsBranchName),

    vcsBranchURI = joinPath(repositoryURI, vcsBranchName);

    public final DynamicVariable<Boolean>
        useUI = newVar(true),
        productionDeployment = newVar(true),
        clean = equalTo(productionDeployment),
        speedUpBuild = and(not(productionDeployment), not(clean)),
        isRemoteEnv = dynamic(new Fun<SessionContext, Boolean>() {
            public Boolean apply(SessionContext $) {
                return $.sys.isRemote();
            }
        }),
        internalInteractiveRun = newVar(false),
        interactiveRun = equalTo(internalInteractiveRun).desc("use it to override interactiveRun"),
        checkDependencies = not(interactiveRun),
        verifyPlugins = equalTo(checkDependencies),
        autoInstallPlugins = newVar(false),
        installationInProgress = newVar(false),
        verbose = newVar(false),
        printHostsToConsole = newVar(true),
        printHostsToBearLog = newVar(true)
    ;

    public final DynamicVariable<Integer>
        promptTimeoutMs = newVar((int) SECONDS.toMillis(10)),
        shortTimeoutMs = newVar((int) SECONDS.toMillis(30)),
        buildTimeoutMs = newVar((int) MINUTES.toMillis(10)),
        installationTimeoutMs = newVar((int) MINUTES.toMillis(60)),
        defaultTimeout = equalTo(buildTimeoutMs),
        appStartTimeoutSec = newVar(240),
        appWaitOthersTimeoutSec = newVar(120)
    ;

    public final DynamicVariable<Stages> stages = new DynamicVariable<Stages>("List of stages. Stage is collection of servers with roles and auth defined for each of the server.");
    public final DynamicVariable<Stage> getStage = dynamic(new Fun<GlobalContext, Stage>() {
        public Stage apply(GlobalContext $) {
            return findStage($);
        }
    });

    public final DynamicVariable<List<String>> activeHosts = undefined();
    public final DynamicVariable<List<String>> activeRoles = undefined();

    public final DynamicVariable<Function<Stage, Collection<Address>>> addressesForStage = dynamic(new Fun<AbstractContext, Function<Stage, Collection<Address>>>() {
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

    public final DynamicVariable<VCSSession> vcs = new DynamicVariable<VCSSession>("vcs", "VCS adapter").setDynamic(new Fun<SessionContext, VCSSession>() {
        public VCSSession apply(SessionContext $) {
            Optional<VcsCLIPlugin> vcsCLI = global.pluginOfInstance(VcsCLIPlugin.class);

            Preconditions.checkArgument(vcsCLI.isPresent(), "add a VCS plugin!");

            return (VCSSession) global.plugins.newSession(vcsCLI.get(), $, $.getCurrentTask());
        }
    }).memoizeIn(SessionContext.class);

    public final DynamicVariable<File>
        scriptsDir = newVar(new File(".bear")),
        globalPropertiesFile = dynamic(new Fun<SessionContext, File>() {
            public File apply(SessionContext $) {
                return new File($.var(scriptsDir), "global.properties");
            }
        });

    public class FileNameGenerator{
        final SessionContext $;

        protected FileNameGenerator(SessionContext $) {
            this.$ = $;
        }

        public String getName(String prefix, String suffix){
            return prefix + RandomStringUtils.randomAlphanumeric(10) + suffix;
        }

        public String getTempPath(String prefix, String suffix){
            return $.joinPath($.var(tempDirPath), getName(prefix, suffix));
        }
    }

    public final DynamicVariable<FileNameGenerator> randomFilePath = dynamic(new Fun<SessionContext, FileNameGenerator>() {
        @Override
        public FileNameGenerator apply(SessionContext $) {return new FileNameGenerator($);}
    });

}
