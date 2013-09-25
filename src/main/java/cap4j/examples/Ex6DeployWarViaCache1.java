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

package cap4j.examples;

import atocha.Atocha;
import cap4j.core.*;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.mysql.MySqlPlugin;
import cap4j.plugins.tomcat.MavenPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.scm.VcsCLI;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static cap4j.core.GlobalContext.getInstance;
import static cap4j.core.GlobalContext.plugin;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Ex6DeployWarViaCache1 {
    private static final Logger logger = LoggerFactory.getLogger(BaseStrategy.class);

    public static void main(String[] args) throws InterruptedException {
        GlobalContextFactory.INSTANCE.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
            @Override
            public List<Class<? extends Plugin>> registerPlugins(VariablesLayer vars) {
                return Lists.<Class<? extends Plugin>>newArrayList(
                    TomcatPlugin.class,
                    GrailsPlugin.class,
                    MySqlPlugin.class,
                    JavaPlugin.class,
                    MavenPlugin.class
                );
            }
        };
        //todo this is not good
        GlobalContextFactory.INSTANCE.globalVarsInitPhase = Ex5DeployWar1.newAtochaSettings(GlobalContextFactory.INSTANCE.getGlobal().cap);
        GlobalContextFactory.INSTANCE.init();

        final GlobalContext global = getInstance();
        final VariablesLayer vars = global.variablesLayer;

        final GrailsPlugin grails = plugin(GrailsPlugin.class);
        final JavaPlugin java = plugin(JavaPlugin.class);
        final TomcatPlugin tomcat = plugin(TomcatPlugin.class);
        final MySqlPlugin mysql = plugin(MySqlPlugin.class);
        final MavenPlugin maven = plugin(MavenPlugin.class);

        final Cap cap = global.cap;

        vars
//            .putS(grails.homePath, "/opt/grails")
//            .putS(java.homePath, "/usr/java/jdk1.6.0_43")
            .putS(cap.sshUsername, "andrey")
            .putS(cap.vcsPassword, global.getProperty("svn.password"))
            .putS(cap.stage, "vms")
            .putS(mysql.dbName, "demodb")
            .putS(java.javaLinuxDistributionName, "jdk-7u25-linux-x64.gz")
            .putS(grails.version, "2.2.4")
//            .putS(vcsBranchName, "branches/rc3_r1201")
        ;

//        vars.putB(clean, true);

        tomcat.warName.setEqualTo(grails.warName);

        cap.stages.defaultTo(
            new Stages()
                .add(new Stage("vms", global)
                    .add(newUnixRemote("vm02", "vm02", global)))
        );

        Cap.newStrategy.setDynamic(new VarFun<BaseStrategy>() {
            public BaseStrategy apply() {
//                GrailsConf.projectWarPath.setEqualTo(
//                    joinPath(vcsBranchLocalPath, GrailsConf.warName)
//                );

                grails.projectPath.setEqualTo(
                    cap.vcsBranchLocalPath
                );

                final BaseStrategy strategy = new BaseStrategy($, global) {
                    @Override
                    protected void step_40_updateRemoteFiles() {
                        logger.info("updating the project, please wait...");

                        StopWatch sw = new StopWatch();
                        sw.start();

                        final VcsCLI vcsCLI = $.var(cap.vcs);

                        final String destPath = $.var(cap.vcsBranchLocalPath);

                        final cap4j.cli.Script script;

                        if (!$.system.exists(destPath)) {
                            script = vcsCLI.checkout($.var(cap.revision), destPath, VcsCLI.emptyParams());
                        } else {
                            script = vcsCLI.sync($.var(cap.revision), destPath, VcsCLI.emptyParams());
                        }

                        script.timeoutMs(600 * 1000);

                        $.system.run(script, vcsCLI.passwordCallback());

                        logger.info("done updating in {}", sw);

                        logger.info("building the project...");

                        String warPath = $.var(grails.releaseWarPath);

                        final boolean warExists = $.system.exists(warPath);
                        if (!warExists || !$.var(global.getPlugin(Atocha.class).reuseWar)) {
                            final GrailsBuildResult r = new GrailsBuilder($, global).build();

                            if (r.result.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        } else {
                            logger.info("war exists and will be reused");
                        }
                    }

                    @Override
                    protected void step_50_whenRemoteUpdateFinished(SessionContext localCtx) {

                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, $.var(cap.appUsername) + "." + $.var(cap.appUsername))
                );

                return strategy;
            }
        });

        global.localCtx.var(cap.getStage).runTask(maven.setup);

        global.shutdown();
    }
}
