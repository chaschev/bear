package test
import bear.annotations.Configuration
import bear.annotations.Project
import bear.core.*
import bear.plugins.DeploymentPlugin
import bear.plugins.misc.Release
import bear.plugins.misc.ReleasesPlugin
import bear.session.Result
import bear.task.TaskCallable
import bear.task.TaskResult
import bear.task.ValueResult
import com.google.common.base.Supplier

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import static bear.core.Role.db
import static bear.core.Role.web
import static bear.task.NamedCallable.named
import static bear.task.TaskResult.OK
import static bear.task.ValueResult.result
import static java.util.Collections.singletonList
import static org.fest.assertions.api.Assertions.assertThat
/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
@Project(shortName = "bear-tests", name = "Shell Examples Demo 1")
@Configuration(
    properties = ".bear/ss-demo",
    stage = "u-3",
    useUI = false,
    user = "andrey"
)
public class TestProject extends BearProject<TestProject> {
    Bear bear;

    DeploymentPlugin deployment
    ReleasesPlugin releases

    static class TestResult {
        TaskResult result;
        Exception exceptionThrown;

        TestResult(TaskResult result)
        {
            this.result = result
        }

        TestResult(Exception exceptionThrown)
        {
            this.exceptionThrown = exceptionThrown
        }
    }

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        bear.stages.set(new Stages(global)
            .addQuick("one", "vm01")
            .addQuick("two", "vm01, vm02")
            .addQuick("three", "vm01, vm02, vm03")
            .addQuick("u-1", "vm04")
            .addQuick("u-2", "vm04, vm05")
            .addQuick("u-3", "vm04, vm05, vm06")
        );

        return global;
    }

    DeploymentPlugin.Builder newDefaultDeployment() {
        return deployment.newBuilder()
            .CheckoutFiles_2({_, task -> OK } as TaskCallable)
            .BuildAndCopy_3({_, task -> } as TaskCallable)
            .StopService_4({_, task -> OK; } as TaskCallable)
            .StartService_6({_, task -> OK; } as TaskCallable)
            .endDeploy()
            .ifRollback()
            .beforeLinkSwitch({_, task -> OK; } as TaskCallable)
            .afterLinkSwitch({_, task -> OK; } as TaskCallable)
            .endRollback()
    }


    static main(args)
    {
        // SETUP
        final TestProject project = new TestProject()
            .setShutdownAfterRun(false)

        def setupNeeded = new AtomicBoolean(false)

        project.run([{_, task ->
            if (!_.sys.exists(_.var(_.bear.applicationPath))) {
                setupNeeded.set(true)
            }

            _.sys.rm(_.var(project.releases.path)).run()
        } as TaskCallable])


        if (setupNeeded.get()) {
            project.setup()
        }

        //these are copy-pasted with slight changes
        project.invoke('syncingPhases_Test')
//        project.invoke('rolesAndHosts_Test')
//        project.invoke('syncingPhases_Test')
//        project.invoke('errorInOne_Checkout');
//        project.invoke('errorInOne_Start');
//        project.invoke('errorInAll_Checkout');
//        project.invoke('deploy');

        project.global.shutdown()
    }

    static class MyResult1 extends TaskResult{
        String message1

        MyResult1(String message) {
            super(Result.OK)

            this.message1 = message
        }
    }

    static class MyResult2 extends TaskResult{
        String message2

        MyResult2(String message) {
            super(Result.OK)

            this.message2 = message
        }
    }

    static class MyResult3 extends TaskResult{
        String message3

        MyResult3(String message) {
            super(Result.OK)

            this.message3 = message
        }
    }

    @Configuration(stage = "u-1")
    public void referencingPhasesAndAccessingFutures_Test()
    {
        useAnnotations = false

        def host = global.var(bear.getStage).addresses[0].name

        def ph1 = named("ph1", { _, task ->
            new MyResult1("r1")
        } as TaskCallable<Void, MyResult1>)

        def ph2 = named("ph2", { _, task ->
            new MyResult2(task.input.message1 + "r2")
        } as TaskCallable<MyResult1, MyResult2>)

        def ph3 = named("ph3", { _, task ->
            new MyResult3(_.getPreviousResult(ph2).message2 + _.getPreviousResult(ph1).message1)
        } as TaskCallable<MyResult2, MyResult3>)

        GlobalTaskRunner runner = run([ph1, ph2, ph3])

        assertThat(runner.result(ph1, host).message1).isEqualTo("r1")
        assertThat(runner.result(ph2, host).message2).isEqualTo("r1r2")
        assertThat(runner.result(ph3, host).message3).isEqualTo("r1r2r1")
    }

    @Configuration(stage = "u-3")
    public void rolesAndHosts_Test()
    {
        useAnnotations = false

        final Stage stage = global.var(bear.getStage)

        def (String h1, String h2, String h3)  = global.var(bear.getStage).addresses.collect { it.name }

        stage.getStages().assignRoleToHosts(web, "$h1, $h2")
        stage.getStages().assignRoleToHosts(db, "$h2, $h3")

        def map = new ConcurrentHashMap<String, Boolean>();

        assertThat(runRoles(map, [web], []).keySet()).containsOnly(h1, h2)
        assertThat(runRoles(map, [db], []).keySet()).containsOnly(h2, h3)
        assertThat(runRoles(map, [db, web], []).keySet()).containsOnly(h1, h2, h3)
        assertThat(runRoles(map, [web], [h3]).keySet()).containsOnly(h1, h2, h3)
    }

    private Map<String, Boolean> runRoles(map, ArrayList<Role> roles, ArrayList<String> csvHosts)
    {
        map.clear()

        global.withMap(
            (global.bear.activeRoles): roles.collect { it.role },
            (global.bear.activeHosts): csvHosts,
            { run([named("runRolesTest", { SessionContext _, task -> map[_.name] = true; OK } as TaskCallable)]) } as Callable
        )

        return map
    }


    @Configuration(stage = "u-2")
    public void syncingPhases_Test()
    {
        useAnnotations = false

        def (String host1, String host2) = global.var(bear.getStage).addresses.collect { it.name }

        def ph1 = named("ph1", { _, task ->
            switch(_.name){
                case host1: Thread.sleep(500); return result("h1, ph1")
                case host2:                    return result("h2, ph1")
            }
        } as TaskCallable<Void, ValueResult<String>>)

        def ph2 = named("ph2", { _, task ->
            switch (_.name){
                case host1: return _.getPreviousResult(ph1);
                case host2:
                    // this will wait for vm01 to complete the operation
                    def r = _.future(ph1, host1).get().value + ", h2, ph2"

                    return result(r)
            }
        } as TaskCallable<Void, ValueResult<String>>)

        def runner = run([ph1, ph2])

        assertThat(runner.result(ph2, host1).value).isEqualTo("h1, ph1")
        assertThat(runner.result(ph2, host2).value).isEqualTo("h1, ph1, h2, ph2")
    }

    @Configuration(stage = "u-2")
    public void  errorInAll_Checkout()
    {
        useAnnotations = false;
        givenNoReleases()

        def (String h1, String h2)  = global.var(bear.getStage).addresses.collect { it.name }

        defaultDeployment.CheckoutFiles_2().setTaskCallable("checkout", {_, task ->
            return TaskResult.error("error on ${_.sys.name}");
        } as TaskCallable);

        //WHEN
        def runner = runTasksWithAnnotations({ ->
            singletonList(defaultDeployment.build())
        } as Supplier);

        //THEN

        assertThat(runner.future("checkout", h1).exception.cause).hasMessage("error on $h1")
        assertThat(runner.future("checkout", h2).exception.cause).hasMessage("error on $h2")

        assertReleasesCount(this, [
            (h1): [pendingCount: 1, releasesCount: 1],
            (h2): [pendingCount: 1, releasesCount: 1]
        ]);
    }

    @Configuration(stage = "u-2")
    public void errorInOne_Start()
    {
        useAnnotations = false;

        givenNoReleases()
        global.removeConst(releases.releaseName)

        AtomicReference<Release> currentRelease = new AtomicReference<>(null)

        run([{_, task ->
            currentRelease.set(_.var(releases.session).currentRelease.get())
            OK
        } as TaskCallable]).throwIfAnyFailed()

        def (String h1, String h2)  = global.var(bear.getStage).addresses.collect { it.name }

        //WHEN

        getDefaultDeployment().startService.setTaskCallable("start", {_, task ->
            return task.phaseParty.index == 0 ?  TaskResult.error("error on ${_.sys.name}") : OK;
        } as TaskCallable);


        def runner = runTasksWithAnnotations({ ->
            singletonList(defaultDeployment.build())
        } as Supplier);

        //THEN

        assertThat(runner.future("start", h1).exception.cause).hasMessage("error on $h1")
        assertThat(runner.future("start", h2).get().ok()).isTrue()

        assertReleasesCount(this, [
            (h1): [pendingCount: 0, releasesCount: 2],
            (h2): [pendingCount: 0, releasesCount: 2]
        ]);

        run([{_, task ->
            // optimistic deployment: few will fall
            // manual rollback in case it's different
            if(_.name == h1){
                assertThat(_.var(releases.session).currentRelease.get().path).isEqualTo(currentRelease.get().path)
            }
            OK
        } as TaskCallable]).throwIfAnyFailed()
    }


    @Configuration(stage = "u-2")
    public void  errorInOne_Checkout()
    {
        useAnnotations = false;
        givenNoReleases()

        AtomicReference<Release> currentRelease = new AtomicReference<>(null)

        run([{_, task ->
            currentRelease.set(_.var(releases.session).currentRelease.get())
            OK
        } as TaskCallable]).throwIfAnyFailed()

        def (String h1, String h2)  = global.var(bear.getStage).addresses.collect { it.name }

        //WHEN

        getDefaultDeployment().checkoutFiles.setTaskCallable("checkout", {_, task ->
            return task.phaseParty.index == 0 ?  TaskResult.error("error on ${_.sys.name}") : OK;
        } as TaskCallable);


        def runner = runTasksWithAnnotations({ ->
            singletonList(defaultDeployment.build())
        } as Supplier);

        //THEN

        assertThat(runner.future("checkout", h1).exception.cause).hasMessage("error on $h1")
        assertThat(runner.future("checkout", h2).get().ok()).isTrue()

        assertReleasesCount(this, [
            (h1): [pendingCount: 1, releasesCount: 1],
            (h2): [pendingCount: 0, releasesCount: 1]
        ]);

        run([named("check current release", {_, task ->
            assertThat(_.var(releases.session).currentRelease.get().path).isEqualTo(currentRelease.get().path)
            OK
        } as TaskCallable)]).throwIfAnyFailed()
    }

    private static assertReleasesCount(TestProject test, Map<String, Map> map)
    {
        test.run([{_, task ->
            def dirs = _.sys.lsQuick(_.var(test.releases.path))

            assertThat(dirs.findAll { it.startsWith("pending_") }).hasSize(map[_.name].pendingCount)
            assertThat(dirs.findAll { !it.startsWith("pending_") && !it.contains("releases.json") && !it.contains("current") }).hasSize(map[_.name].releasesCount)

            OK
        } as TaskCallable]).throwIfAnyFailed()
    }


    private givenNoReleases()
    {
        defaultDeployment = newDefaultDeployment()

        run([named("empty releases", {_, task ->
            _.sys.rm(_.var(releases.path)).run()
            _.sys.mkdirs(_.var(releases.path)).run()
        } as TaskCallable)])

        // do a successful deployment
        deploy().throwIfAnyFailed()
    }
}
