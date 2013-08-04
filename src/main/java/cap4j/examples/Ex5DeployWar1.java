package cap4j.examples;

import cap4j.*;
import cap4j.plugins.grails.GrailsBuildResult;
import cap4j.plugins.grails.GrailsBuilder;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import cap4j.session.DynamicVariable;
import cap4j.strategy.BaseStrategy;
import cap4j.strategy.SymlinkEntry;
import cap4j.task.Tasks;
import com.google.common.base.Function;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static cap4j.CapConstants.*;
import static cap4j.plugins.tomcat.TomcatPlugin.tomcatWarPath;
import static cap4j.plugins.tomcat.TomcatPlugin.tomcatWebapps;
import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;
import static cap4j.session.VariableUtils.joinPath;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class Ex5DeployWar1 {
    public static class AtochaConstants {
        public static final DynamicVariable<Boolean>
            reuseWar = CapConstants.bool("reuseWar", "will skip building WAR").defaultTo(false);

    }

    public static void main(String[] args) throws InterruptedException {
        GlobalContextFactory.INSTANCE.globalVarsInitPhase = newAtochaSettings();
        GlobalContextFactory.INSTANCE.init();

        final Variables vars = GlobalContext.INSTANCE.variables;

        vars
            .putS(GrailsPlugin.grailsPath, "c:/dev/grails-2.1.0")
            .putS(GrailsPlugin.projectPath, "c:/Users/achaschev/prj/atocha");

        TomcatPlugin.init();

        final Stage pacDev = new Stage("pac-dev")
//            .add(newUnixRemote("server1", "chaschev", "1", "192.168.25.66"))
            .add(newUnixRemote("server1", "192.168.25.66"))
            ;

        CapConstants.newStrategy.setDynamic(new Function<VarContext, BaseStrategy>() {
            public BaseStrategy apply(VarContext ctx) {
                final BaseStrategy strategy = new BaseStrategy(ctx) {
                    @Override
                    protected List<File> step_20_prepareLocalFiles(VarContext localCtx) {
                        File rootWar = new File(localCtx.var(GrailsPlugin.projectWarPath));

                        if (!rootWar.exists() || !localCtx.var(AtochaConstants.reuseWar)) {
                            final GrailsBuildResult r = new GrailsBuilder(localCtx).build();

                            if (r.result.nok()) {
                                throw new IllegalStateException("failed to build WAR");
                            }
                        }

                        return Collections.singletonList(rootWar);
                    }
                };

                strategy.getSymlinkRules().add(
                    new SymlinkEntry("ROOT.war", tomcatWarPath, ctx.var(appUsername) + "." + ctx.var(appUsername))
                );

                return strategy;
            }
        });

        pacDev.runTask(Tasks.deploy);

        GlobalContext.INSTANCE.shutdown();

    }

    public static GlobalContextFactory.GlobalVarsInitPhase newAtochaSettings() {
        return new GlobalContextFactory.GlobalVarsInitPhase() {
            @Override
            public void setVars(Variables vars) {
                vars
                    .putS(applicationName, "atocha")
                    .putB(productionDeployment, false)
                    .putB(speedUpBuild, true)
                    .putB(AtochaConstants.reuseWar, true)
                    .putS(vcsType, "svn")
                    .putS(sshUsername, "chaschev")
                    .putS(sshPassword, "aaaaaa")
                    .putS(appUsername, "tomcat")
                ;
            }
        };
    }
}
