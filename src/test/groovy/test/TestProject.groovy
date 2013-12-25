package test

import bear.annotations.Configuration
import bear.annotations.Project
import bear.core.*
import bear.plugins.DeploymentPlugin
import bear.plugins.misc.Release
import bear.plugins.misc.ReleasesPlugin
import bear.task.TaskCallable
import bear.task.TaskResult
import com.google.common.base.Function
import com.google.common.base.Supplier

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import static bear.task.TaskResult.OK
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
            .addQuick("u-1", "vm06")
            .addQuick("u-2", "vm04, vm05")
            .addQuick("u-3", "vm04, vm05, vm06")
        );

        return global;
    }

    DeploymentPlugin.Builder newDefaultDeployment()
    {
        return deployment.newBuilder()
            .CheckoutFiles_2({ _, task, i -> println "HEHEHE"; OK } as TaskCallable)
            .BuildAndCopy_3({ _, task, i -> } as TaskCallable)
            .StopService_5({ _, task, i -> OK; } as TaskCallable)
            .StartService_8({ _, task, i -> OK; } as TaskCallable)
            .endDeploy()
            .ifRollback()
            .beforeLinkSwitch({ _, task, input -> OK; } as TaskCallable)
            .afterLinkSwitch({ _, task, input -> OK; } as TaskCallable)
            .endRollback()
    }

    void deploy(Function<GridBuilder, Void> f)
    {
        preRunHook = f;
        super.deploy()
    }



    static main(args)
    {
        // SETUP
        final TestProject test = new TestProject()
            .setShutdownAfterRun(false)

        def setupNeeded = new AtomicBoolean(false)

        test.run([{ _, task, i ->
            if (!_.sys.exists(_.var(_.bear.applicationPath))) {
                setupNeeded.set(true)
            }

            _.sys.rm(_.var(test.releases.path)).run()
        } as TaskCallable])


        if (setupNeeded.get()) {
            test.setup()
        }

        //these are copy-pasted with slight changes
        errorInAll_Checkout(test)
        errorInOne_Checkout(test);
        errorInOne_Start(test);

        test.global.shutdown()
    }

    private static errorInAll_Checkout(TestProject test)
    {

        given(test)

        def hosts = test.global.var(test.bear.getStage).addresses.collect { it.name }

        test.getDefaultDeployment().CheckoutFiles_2().setTaskCallable("checkout", { _, task, input ->
            return TaskResult.error("error on ${_.sys.name}");
        } as TaskCallable);

        //WHEN
        def runner = test.runTasksWithAnnotations({ ->
            singletonList(test.defaultDeployment.build())
        } as Supplier);

        //THEN

        assertThat(runner.future("checkout", hosts[0]).get().exception.cause).hasMessage("error on ${hosts[0]}")
        assertThat(runner.future("checkout", hosts[1]).get().exception.cause).hasMessage("error on ${hosts[1]}")

        assertReleasesCount(test, [
            (hosts[0]): [pendingCount: 1, releasesCount: 1],
            (hosts[1]): [pendingCount: 1, releasesCount: 1]
        ]);


    }

    private static errorInOne_Start(TestProject test)
    {
        given(test)
        test.global.removeConst(test.releases.releaseName)

        AtomicReference<Release> currentRelease = new AtomicReference<>(null)

        test.run([{ _, task, input ->
            currentRelease.set(_.var(test.releases.session).currentRelease.get())
            OK
        } as TaskCallable]).throwIfAnyFailed()

        def hosts = test.global.var(test.bear.getStage).addresses.collect { it.name }

        //WHEN

        test.getDefaultDeployment().startService.setTaskCallable("start", { _, task, input ->
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

        test.run([{ _, task, input ->
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
        given(test)

        AtomicReference<Release> currentRelease = new AtomicReference<>(null)

        test.run([{ _, task, input ->
            currentRelease.set(_.var(test.releases.session).currentRelease.get())
            OK
        } as TaskCallable]).throwIfAnyFailed()

        def hosts = test.global.var(test.bear.getStage).addresses.collect { it.name }

        //WHEN

        test.getDefaultDeployment().checkoutFiles.setTaskCallable("checkout", { _, task, input ->
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

        test.run([{ _, task, input ->
            assertThat(_.var(test.releases.session).currentRelease.get().path).isEqualTo(currentRelease.get().path)
            OK
        } as TaskCallable]).throwIfAnyFailed()
    }

    private static assertReleasesCount(TestProject test, Map<String, Map> map)
    {
        test.run([{ _, task, i ->
            final List<String> dirs = _.sys.lsQuick(_.var(test.releases.path))


            assertThat(dirs.findAll { it.startsWith("pending_") }).hasSize(map[_.name].pendingCount)
            assertThat(dirs.findAll { !it.startsWith("pending_") && !it.contains("releases.json") && !it.contains("current") }).hasSize(map[_.name].releasesCount)

            OK
        } as TaskCallable]).throwIfAnyFailed()
    }


    private static given(TestProject test)
    {
        test.defaultDeployment = test
            .newDefaultDeployment()

        test.run([{ _, task, i ->
            _.sys.rm(_.var(test.releases.path)).run()
        } as TaskCallable])

        // do a successful deployment
        test.deploy().throwIfAnyFailed()
    }
}
