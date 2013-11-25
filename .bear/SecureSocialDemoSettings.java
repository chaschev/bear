import bear.core.*;
import bear.plugins.java.JavaPlugin;
import bear.plugins.maven.MavenPlugin;
import bear.plugins.mongo.MongoDbPlugin;
import bear.vcs.GitCLIPlugin;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SecureSocialDemoSettings extends IBearSettings {
    JavaPlugin java;
    MavenPlugin maven;
    Bear bear;
    GlobalContext global;
    GitCLIPlugin git;
    MongoDbPlugin mongo;

    public SecureSocialDemoSettings(GlobalContextFactory factory) {
        super(factory);
    }

    public SecureSocialDemoSettings(GlobalContextFactory factory, String resource) {
        super(factory, resource);
    }

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception {
        factory.init(this);

        maven.version.set("3.0.5");

//        mongo.version.set("LATEST");

        java.versionName.set("jdk-7u40-linux-x64");
        java.version.set("1.7.0_40");

        Stages stages = new Stages(global);

        bear.stages.defaultTo(
            stages
                .add(
                    new Stage("one")
                    .addHosts(stages.hosts("vm01")))
                .add(
                    new Stage("two")
                        .addHosts(stages.hosts("vm01, vm02")))
                .add(
                    new Stage("three")
                        .addHosts(stages.hosts("vm01, vm02, vm03"))
                ))
        ;

        return global;
    }
}
