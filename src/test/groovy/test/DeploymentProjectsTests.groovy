package test
import bear.core.BearProject
import bear.core.GlobalContext
import bear.core.GlobalContextFactory
import bear.core.SessionContext
import bear.plugins.misc.ReleasesPlugin
import bear.task.TaskCallable
import examples.java.GrailsTomcatDemoProject
import examples.java.SecureSocialDemoProject
import examples.nodejs.DrywallDemoProject
import examples.nodejs.NodeExpressMongooseDemoProject
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static bear.task.NamedCallable.named
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Ignore
class DeploymentProjectsTests {

    public runProject(Class<? extends BearProject> aClass){
        final BearProject project = aClass.newInstance()

        project.global.put(project.global.bear.stage, "u-3")
        project.global.put(project.global.bear.useUI, Boolean.FALSE)

        project.shutdownAfterRun = false

        // uninstall tools && delete releases
        project.run([named("uninstall-all-and-remove-releases", { SessionContext _, task ->
            _.sys.rm(_.var(_.bear.toolsInstallDirPath)).sudo().force().run()
            _.sys.rm(_.var(_.plugin(ReleasesPlugin).path)).sudo().force().run()
        } as TaskCallable)])

        project.setup().throwIfAnyFailed()
        project.setup().throwIfAnyFailed()

        project.deploy().throwIfAnyFailed()

        project.pulse().throwIfError()

        project.stop().throwIfAnyFailed()
    }

    @Before
    public void setUp() throws Exception
    {
        // reset global
        GlobalContextFactory.INSTANCE.global.shutdown()
        GlobalContextFactory.INSTANCE.global = GlobalContext.INSTANCE = new GlobalContext()
    }

    @Test(timeout = 600000L)
    public void testPetclinic() throws Exception
    {
        runProject(GrailsTomcatDemoProject)
    }

    @Test(timeout = 600000L)
    public void testSecureSocial() throws Exception
    {
        runProject(SecureSocialDemoProject)
    }

    @Test(timeout = 300000L)
    public void testDrywall() throws Exception
    {
        runProject(DrywallDemoProject)
    }

    @Test(timeout = 300000L)
    public void testNodeExpressDemo() throws Exception
    {
        runProject(NodeExpressMongooseDemoProject)
    }
}
