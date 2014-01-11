package test

import bear.task.TaskCallable
import org.junit.*
import org.junit.runners.MethodSorters

import java.util.concurrent.atomic.AtomicBoolean

import static bear.task.NamedCallable.named
//@Category(IntegrationTests)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore
public class TestProjectTests {
    static private TestProject project

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        println "before class"
        project = new TestProject()
            .setShutdownAfterRun(false)

        def setupNeeded = new AtomicBoolean(false)

        project.run([named("testProject.beforeClass", {_, task ->
            if (!_.sys.exists(_.var(_.bear.applicationPath))) {
                setupNeeded.set(true)
            }

            _.sys.rm(_.var(project.releases.path)).run()
        } as TaskCallable)])

        if (true) {
            project.setup()
        }

        project.run([named("testProject.beforeClass", {_, task ->
            println(_.sys.exists(_.var(project.releases.path)))
        } as TaskCallable)])
    }

    @Before
    public void setUp() throws Exception
    {
        // a hack for speed up (should reload global instead)
        // stage is set to 'u-2' in the clean up above
        // so it can't be redefined in the following annotations
        project.global.removeConst(project.bear.stage)
        project.global.put(project.bear.useUI, false)
    }

    @Test
    public void t1_rolesAndHosts_Test() throws Exception
    {
        project.invoke('rolesAndHosts_Test')
    }

    @Test
    public void t2_referencingPhasesAndAccessingFutures_Test() throws Exception
    {
        project.invoke('referencingPhasesAndAccessingFutures_Test')
    }

    @Test
    public void t3_syncingPhases_Test() throws Exception
    {
        project.invoke('syncingPhases_Test')
    }

    @Test
    public void t4_errorInOne_Checkout() throws Exception
    {
        project.invoke('errorInOne_Checkout');
    }

    @Test
    public void t5_errorInAll_Checkout() throws Exception
    {
        project.invoke('errorInAll_Checkout');
    }

    @Test
    public void t6_errorInOne_Start() throws Exception
    {
        project.invoke('errorInOne_Start');
    }
}
