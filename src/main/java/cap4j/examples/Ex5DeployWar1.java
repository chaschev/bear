package cap4j.examples;

import atocha.AtochaConstants;
import cap4j.core.*;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static cap4j.core.GlobalContext.*;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class Ex5DeployWar1 {

    public static void main(String[] args) throws InterruptedException {
        GlobalContextFactory.INSTANCE.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
            @Override
            public List<Class<? extends Plugin>> registerPlugins(Variables vars) {
                return Lists.<Class<? extends Plugin>>newArrayList(
                    TomcatPlugin.class,
                    GrailsPlugin.class
                );
            }
        };
        GlobalContextFactory.INSTANCE.globalVarsInitPhase = newAtochaSettings(GlobalContextFactory.INSTANCE.getGlobalContext().cap, AtochaConstants.INSTANCE);
        GlobalContextFactory.INSTANCE.init();

        final GlobalContext global = GlobalContext.getInstance();
        final Variables vars = global.variables;

        final CapConstants cap = global.cap;

        final GrailsPlugin grails = plugin(GrailsPlugin.class);
        final JavaPlugin java = plugin(JavaPlugin.class);
        final TomcatPlugin tomcat = plugin(TomcatPlugin.class);

        vars
            .putS(grails.homePath, "c:/dev/grails-2.1.0")
            .putS(grails.projectPath, "c:/Users/achaschev/prj/atocha");

        final Stage pacDev = new Stage("pac-dev", getInstance())
//            .add(newUnixRemote("server1", "chaschev", "1", "192.168.25.66"))
            .add(newUnixRemote("server1", "192.168.25.66", global))
            ;

        CapConstants.newStrategy.setDynamic(new Function<SessionContext, BaseStrategy>() {
            public BaseStrategy apply(SessionContext ctx) {
                final BaseStrategy strategy = new BaseStrategy(ctx, global) {
                    @Override
                    protected List<File> step_20_prepareLocalFiles(SessionContext localCtx) {
                        File rootWar = new File(localCtx.var(grails.projectWarPath));

                        if (!rootWar.exists() || !localCtx.var(AtochaConstants.INSTANCE.reuseWar)) {
                            final GrailsBuildResult r = new GrailsBuilder(localCtx, global).build();

                            if (r.result.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }

                        return Collections.singletonList(rootWar);
                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcat.warPath, ctx.var(cap.appUsername) + "." + ctx.var(cap.appUsername))
                );

                return strategy;
            }
        });

        pacDev.runTask(tasks().deploy);

        global.shutdown();

    }

    public static GlobalContextFactory.GlobalVarsInitPhase newAtochaSettings(CapConstants cap1, AtochaConstants atocha) {
        final CapConstants cap = cap1;

        return new GlobalContextFactory.GlobalVarsInitPhase() {
            @Override
            public void setVars(Variables vars) {
                vars
                    .putS(cap.applicationName, "atocha")
                    .putB(cap.productionDeployment, false)
                    .putB(cap.speedUpBuild, true)
                    .putB(AtochaConstants.INSTANCE.reuseWar, true)
                    .putS(cap.vcsType, "svn")
                    .putS(cap.repositoryURI, "svn+ssh://dev.afoundria.com/var/svn/repos/atocha")
                    .putS(cap.sshUsername, "chaschev")
                    .putS(cap.sshPassword, "aaaaaa")
                    .putS(cap.appUsername, "tomcat")
                ;
            }
        };
    }
}
