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

package cap4j.smoke;

import atocha.Atocha;
import cap4j.core.*;
import cap4j.main.Cap4j;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.MavenPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.scm.GitCLI;
import cap4j.scm.VcsCLI;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PluginsTests {
    private static final Logger logger = LoggerFactory.getLogger(PluginsTests.class);

    public static class SetupPluginsSettings extends ICapSettings {
        GrailsPlugin grails;
        JavaPlugin java;
        Cap cap;
        TomcatPlugin tomcat;

        protected SetupPluginsSettings(GlobalContextFactory factory) {
            super(factory);
        }

        @Override
        protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
            final GlobalContext global = factory.getGlobal();

            factory.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
                @Override
                public List<Class<? extends Plugin>> registerPlugins(VariablesLayer vars) {
                    return Lists.newArrayList(
                        JavaPlugin.class,
                        MavenPlugin.class,
                        TomcatPlugin.class,
                        GrailsPlugin.class,
                        GitCLI.class
                    );
                }
            };

            factory.init();


            tomcat = global.getPlugin(TomcatPlugin.class);
            grails = global.getPlugin(GrailsPlugin.class);
            java = global.getPlugin(JavaPlugin.class);
            cap = global.cap;

            tomcat.warName.setEqualTo(grails.warName);

            cap.stages.defaultTo(
                new Stages()
                    .add(new Stage("vm02", global)
                        .add(newUnixRemote("vm02", "vm02", global)))
            );

            Cap.newStrategy.setDynamic(new VarFun<BaseStrategy>() {

                public BaseStrategy apply() {

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

                            final cap4j.cli.Script line;

                            if (!$.system.exists(destPath)) {
                                line = vcsCLI.checkout($.var(cap.revision), destPath, VcsCLI.emptyParams());
                            } else {
                                line = vcsCLI.sync($.var(cap.revision), destPath, VcsCLI.emptyParams());
                            }

                            line.timeoutMs(600 * 1000);

                            $.system.run(line, vcsCLI.passwordCallback());

                            logger.info("done updating in {}", sw);

                            logger.info("building the project...");

                            String warPath = $.var(grails.releaseWarPath);

                            if (!$.system.exists(warPath) || !$.var(global.getPlugin(Atocha.class).reuseWar)) {
                                final GrailsBuildResult r = new GrailsBuilder($, global).build();

                                if (r.result.nok()) {
                                    throw new IllegalStateException("failed to build WAR");
                                }
                            }
                        }

                        @Override
                        protected void step_50_whenRemoteUpdateFinished(SessionContext localCtx) {

                        }
                    };

                    strategy.getSymlinkRules().add(
                        new SymlinkEntry("ROOT.war", tomcat.warPath, global.var(cap.appUsername) + "." + global.var(cap.appUsername))
                    );

                    return strategy;
                }
            });

            return global;
        }
    }

    public static class SetupPluginsScript extends Script {
        @Override
        public void run() throws Exception {
            cap.stage.defaultTo("vm02");
            cap.task.defaultTo("setup");

            global.run();
            global.shutdown();
        }

        public static void main(String[] args) throws Exception {
            new Cap4j.Cap4jRunner(new SetupPluginsSettings(GlobalContextFactory.INSTANCE).loadProperties(
                PluginsTests.class.getResourceAsStream("/test.properties")
            ), new SetupPluginsScript()).run();
        }
    }

    public static class GithubGrailsAppScript extends Script {
        @Override
        public void run() throws Exception {
            cap.stage.defaultTo("vm02");
            cap.task.defaultTo("deploy");

            cap.vcsBranchName.defaultTo("master");

            global.run();
            global.shutdown();
        }

        public static void main(String[] args) throws Exception {
            new Cap4j.Cap4jRunner(new SetupPluginsSettings(GlobalContextFactory.INSTANCE).loadProperties(
                PluginsTests.class.getResourceAsStream("/test.properties")
            ), new GithubGrailsAppScript()).run();
        }
    }
}
