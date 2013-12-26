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
    stage = "u-2",
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
        final TestProject test = new TestProject()
            .setShutdownAfterRun(false)

        def setupNeeded = new AtomicBoolean(false)

        test.run([{_, task ->
            if (!_.sys.exists(_.var(_.bear.applicationPath))) {
                setupNeeded.set(true)
            }

            _.sys.rm(_.var(test.releases.path)).run()
        } as TaskCallable])


        if (setupNeeded.get()) {
            test.setup()
        }

        //these are copy-pasted with slight changes
//        test.invoke('referencingPhasesAndAccessingFutures_Test')
        test.invoke('rolesAndHosts_Test')
//        test.invoke('syncingPhases_Test')
//        errorInOne_Checkout(test);
//        errorInOne_Start(test);

        test.global.shutdown()
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
    private referencingPhasesAndAccessingFutures_Test()
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
    private rolesAndHosts_Test()
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
            { run([({ SessionContext _, task -> map[_.name] = true; OK } as TaskCallable)]) } as Callable
        )

        return map
    }


    @Configuration(stage = "u-2")
    private syncingPhases_Test()
    {
        useAnnotations = false

        def (String host1, String host2) = global.var(bear.getStage).addresses.collect { it.name }

        def ph1 = named("ph1", { _, task ->
            [
                (host1) : {Thread.sleep(500); result("h1, ph1")},
                (host2) : {                   result("h2, ph1")}
            ].get(_.name)();
        } as TaskCallable<Void, ValueResult<String>>)

        def ph2 = named("ph2", { _, task ->
            [
                (host1) : {_.getPreviousResult(ph1)},
                (host2) : {
                    assertThat(_.future(ph1, host1).isDone()).isFalse()
                    def r = _.future(ph1, host1).get().value + ", h2, ph2"
                    assertThat(_.future(ph1, host1).isDone()).isTrue()

                    result(r)
                }
            ].get(_.name)();

        } as TaskCallable<Void, ValueResult<String>>)

        GlobalTaskRunner runner = run([ph1, ph2])

        assertThat(runner.result(ph2, host1).value).isEqualTo("h1, ph1")
        assertThat(runner.result(ph2, host2).value).isEqualTo("h1, ph1, h2, ph2")
    }


    private errorInAll_Checkout()
    {
        useAnnotations = false;
        givenNoReleases(this)

        def hosts = global.var(bear.getStage).addresses.collect { it.name }

        defaultDeployment.CheckoutFiles_2().setTaskCallable("checkout", {_, task ->
            return TaskResult.error("error on ${_.sys.name}");
        } as TaskCallable);

        //WHEN
        def runner = runTasksWithAnnotations({ ->
            singletonList(defaultDeployment.build())
        } as Supplier);

        //THEN

        assertThat(runner.future("checkout", hosts[0]).get().exception.cause).hasMessage("error on ${hosts[0]}")
        assertThat(runner.future("checkout", hosts[1]).get().exception.cause).hasMessage("error on ${hosts[1]}")

        assertReleasesCount(this, [
            (hosts[0]): [pendingCount: 1, releasesCount: 1],
            (hosts[1]): [pendingCount: 1, releasesCount: 1]
        ]);
    }

    private static errorInOne_Start(TestProject test)
    {
        givenNoReleases(test)
        test.global.removeConst(test.releases.releaseName)

        AtomicReference<Release> currentRelease = new AtomicReference<>(null)

        test.run([{_, task ->
            currentRelease.set(_.var(test.releases.session).currentRelease.get())
            OK
        } as TaskCallable]).throwIfAnyFailed()

        def hosts = test.global.var(test.bear.getStage).addresses.collect { it.name }

        //WHEN

        test.getDefaultDeployment().startService.setTaskCallable("start", {_, task ->
            return task.phaseParty.index == 0 ?  TaskResult.error("error on ${_.sys.name}") : OK;
        } as TaskCallable);


        def runner = test.runTasksWithAnnotations({ ->
            singletonList(test.defaultDeployment.build())
        } as Supplier);

        //THEN

        assertThat(runner.future("start", hosts[0]).get().exception.cause).hasMessage("error on ${hosts[0]}")
        assertThat(runner.future("start", hosts[1]).get().get().ok()).isTrue()

        assertReleasesCount(test, [
            (hosts[0]): [pendingCount: 0, releasesCount: 2],
            (hosts[1]): [pendingCount: 0, releasesCount: 2]
        ]);

        test.run([{_, task ->
            // optimistic deployment: few will fall
            // manual rollback in case it's different
            if(_.name == hosts[0]){
                assertThat(_.var(test.releases.session).currentRelease.get().path).isEqualTo(currentRelease.get().path)
            }
            OK
        } as TaskCallable]).throwIfAnyFailed()
    }


    private static errorInOne_Checkout(TestProject test)
    {
        givenNoReleases(test)

        AtomicReference<Release> currentRelease = new AtomicReference<>(null)

        test.run([{_, task ->
            currentRelease.set(_.var(test.releases.session).currentRelease.get())
            OK
        } as TaskCallable]).throwIfAnyFailed()

        def hosts = test.global.var(test.bear.getStage).addresses.collect { it.name }

        //WHEN

        test.getDefaultDeployment().checkoutFiles.setTaskCallable("checkout", {_, task ->
            return task.phaseParty.index == 0 ?  TaskResult.error("error on ${_.sys.name}") : OK;
        } as TaskCallable);


        def runner = test.runTasksWithAnnotations({ ->
            singletonList(test.defaultDeployment.build())
        } as Supplier);

        //THEN

        assertThat(runner.future("checkout", hosts[0]).get().exception.cause).hasMessage("error on ${hosts[0]}")
        assertThat(runner.future("checkout", hosts[1]).get().get().ok()).isTrue()

        assertReleasesCount(test, [
            (hosts[0]): [pendingCount: 1, releasesCount: 1],
            (hosts[1]): [pendingCount: 0, releasesCount: 1]
        ]);

        test.run([{_, task ->
            assertThat(_.var(test.releases.session).currentRelease.get().path).isEqualTo(currentRelease.get().path)
            OK
        } as TaskCallable]).throwIfAnyFailed()
    }

    private static assertReleasesCount(TestProject test, Map<String, Map> map)
    {
        test.run([{_, task ->
            final List<String> dirs = _.sys.lsQuick(_.var(test.releases.path))


            assertThat(dirs.findAll { it.startsWith("pending_") }).hasSize(map[_.name].pendingCount)
            assertThat(dirs.findAll { !it.startsWith("pending_") && !it.contains("releases.json") && !it.contains("current") }).hasSize(map[_.name].releasesCount)

            OK
        } as TaskCallable]).throwIfAnyFailed()
    }


    private givenNoReleases(TestProject test)
    {
        defaultDeployment = newDefaultDeployment()

        run([{_, task ->
            _.sys.rm(_.var(test.releases.path)).run()
        } as TaskCallable])

        // do a successful deployment
        deploy().throwIfAnyFailed()
    }
}
