package cap4j.smoke;

import cap4j.core.*;
import cap4j.main.Cap4j;
import cap4j.plugins.Plugin;
import cap4j.plugins.grails.GrailsPlugin;
import cap4j.plugins.java.JavaPlugin;
import cap4j.plugins.tomcat.MavenPlugin;
import cap4j.plugins.tomcat.TomcatPlugin;
import com.google.common.collect.Lists;

import java.util.List;

import static cap4j.session.GenericUnixRemoteEnvironment.newUnixRemote;

/**
 * User: chaschev
 * Date: 9/25/13
 */
public class PluginsTests {
    public static class SetupPluginsSettings extends ICapSettings {
        Cap cap;

        protected SetupPluginsSettings(GlobalContextFactory factory) {
            super(factory);
        }

        @Override
        protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
            final GlobalContext global = factory.getGlobal();

            factory.registerPluginsPhase = new GlobalContextFactory.RegisterPluginsPhase() {
                @Override
                public List<Class<? extends Plugin>> registerPlugins(Variables vars) {
                    return Lists.newArrayList(
                        JavaPlugin.class,
                        MavenPlugin.class,
                        TomcatPlugin.class,
                        GrailsPlugin.class
                    );
                }
            };

            cap = global.cap;

            factory.init();

            cap.stages.defaultTo(
                new Stages()
                    .add(new Stage("vm02", global)
                        .add(newUnixRemote("vm02", "vm02", global)))
            );

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
}
