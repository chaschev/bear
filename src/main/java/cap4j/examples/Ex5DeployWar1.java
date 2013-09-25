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
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static cap4j.core.GlobalContext.*;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Ex5DeployWar1 {

    public static void main(String[] args) throws InterruptedException {
        GlobalContextFactory.INSTANCE.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
            @Override
            public List<Class<? extends Plugin>> registerPlugins(VariablesLayer vars) {
                return Lists.<Class<? extends Plugin>>newArrayList(
                    TomcatPlugin.class,
                    GrailsPlugin.class
                );
            }
        };
        GlobalContextFactory.INSTANCE.globalVarsInitPhase = newAtochaSettings(GlobalContextFactory.INSTANCE.getGlobal().cap);
        GlobalContextFactory.INSTANCE.init();

        final GlobalContext global = GlobalContext.getInstance();
        final VariablesLayer vars = global.variablesLayer;

        final Cap cap = global.cap;

        final GrailsPlugin grails = plugin(GrailsPlugin.class);
        final JavaPlugin java = plugin(JavaPlugin.class);
        final TomcatPlugin tomcat = plugin(TomcatPlugin.class);

        vars
            .putS(grails.homePath, "c:/dev/grails-2.1.0")
            .putS(grails.projectPath, "c:/Users/achaschev/prj/atocha");

        final Stage pacDev = new Stage("pac-dev", getInstance())
//            .add(newUnixRemote("server1", "chaschev", "1", "192.168.25.66"))
            .add(newUnixRemote("server1", "vm02", global));

        Cap.newStrategy.setDynamic(new VarFun<BaseStrategy>() {
            public BaseStrategy apply() {
                final BaseStrategy strategy = new BaseStrategy($, global) {
                    @Override
                    protected List<File> step_20_prepareLocalFiles(SessionContext localCtx) {
                        File rootWar = new File(localCtx.var(grails.projectWarPath));

                        if (!rootWar.exists() || !localCtx.var(global.getPlugin(Atocha.class).reuseWar)) {
                            final GrailsBuildResult r = new GrailsBuilder(localCtx, global).build();

                            if (r.result.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }

                        return Collections.singletonList(rootWar);
                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, $.var(cap.appUsername) + "." + $.var(cap.appUsername))
                );

                return strategy;
            }
        });

        pacDev.runTask(tasks().deploy);

        global.shutdown();

    }

    public static GlobalContextFactory.GlobalVarsInitPhase newAtochaSettings(Cap cap1) {
        final Cap cap = cap1;

        return new GlobalContextFactory.GlobalVarsInitPhase() {
            @Override
            public void setVars(VariablesLayer vars) {
                vars
                    .putS(cap.applicationName, "atocha")
                    .putB(cap.productionDeployment, false)
                    .putB(cap.speedUpBuild, true)
                    .putS(cap.vcsType, "svn")
                    .putS(cap.repositoryURI, "svn://vm02/svnrepos/atocha")
                    .putS(cap.appUsername, "tomcat")
                ;
            }
        };
    }
}
