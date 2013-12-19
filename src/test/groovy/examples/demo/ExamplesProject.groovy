package examples.demo
import bear.annotations.Configuration
import bear.annotations.Project
import bear.core.*
import bear.core.except.NoSuchFileException
import bear.core.except.PermissionsException
import bear.task.TaskCallable

import static bear.task.TaskResult.OK
import static com.google.common.base.Preconditions.checkArgument
import static groovy.test.GroovyAssert.shouldFail
import static org.fest.assertions.api.Assertions.assertThat

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
@Project(shortName = "examples-demo", name = "Shell Examples Demo 1")
@Configuration(
    propertiesFile = ".bear/ss-demo",
    stage = "one",
    useUI = false
)
public class ExamplesProject extends BearProject<ExamplesProject> {
    Bear bear;

    @Override
    protected GlobalContext configureMe(GlobalContextFactory factory) throws Exception
    {
        bear.stages.defaultTo(new Stages(global)
            .addSimple("one", "vm01")
            .addSimple("two", "vm01, vm02")
            .addSimple("three", "vm01, vm02, vm03"));

        return global;
    }

    static TaskCallable fileOperations = { _, task, i ->
        final user = _.var(_.bear.sshUsername)

        println "${_.host}: ${_.sys.fileSizeAsLong('texty')}";
        println "${_.host}: ${_.sys.capture('cat texty')}";

        _.sys.writeString("hi from ${_.host}").toPath("file1").run()

        checkArgument(_.sys.capture('cat file1').equals("hi from ${_.host}".toString()))
        checkArgument(_.sys.exists('file1'))

        _.sys.move('file1').to('file2').run()

        checkArgument(!_.sys.exists('file1') && _.sys.exists('file2'))

        _.sys.move('file2').to('file3')
            .withUser("root")
            .withPermissions("o-r,g-r")
            .sudo().run()

        checkArgument(!_.sys.exists('file2') && _.sys.exists('file3'))

        shouldFail(PermissionsException, { _.sys.capture('cat file3') })
        assertThat(_.sys.captureBuilder('cat file3').run().ok()).isFalse()

        _.sys.copy('file3').to('file4').sudo().withUser(user).withPermissions("o+r,g+r").run()

        assertThat(_.sys.capture('cat file4') == "hi from ${_.host}").isTrue()

        _.sys.link('file5').toSource('file4').run()

        checkArgument(_.sys.readString("/home/$user/file5", null) == "hi from ${_.host}")

        _.sys.mkdirs("testRootDir1").sudo().withUser("root.root").withPermissions("o-r,g-r").run()
        _.sys.mkdirs("testDir2").run()

        shouldFail(PermissionsException, { _.sys.lsQuick("testRootDir1") })

        assertThat(_.sys.lsQuick(".")).contains("testRootDir1")
        assertThat(_.sys.lsQuick("testDir2")).isEmpty()

        shouldFail(NoSuchFileException, { _.sys.lsQuick("noSuchDir") })

        _.sys.permissions("testDir2").sudo().withUser("root.root").withPermissions("o-r").run();

        shouldFail(PermissionsException, { _.sys.lsQuick("testDir2") })

        _.sys.rm("file*", "test*").sudo().force().run()

        OK
    } as TaskCallable

    static main(args)
    {
        new ExamplesProject().run([fileOperations])
    }
}
