package examples.demo

import bear.console.ConsoleCallback
import bear.console.ConsoleCallbackResult
import bear.core.GridBuilder
import bear.core.SessionContext
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskResult

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

def sample()
{
    //groovyc gives stackoverflow error when declared dynamically
    GridBuilder testCapturesGrid = new GridBuilder().add(new TaskCallable() {
        @Override
        TaskResult call(SessionContext _, Task task, Object input) throws Exception
        {
                def callback = { console, buffer, wholeText ->
                    println "HEY!! ${buffer}";

                    return ConsoleCallbackResult.CONTINUE;
                } as ConsoleCallback

                println "HEY-STEP 0!!"
                def pwd = _.sys.capture("pwd", callback)
                println "expected pwd: '${pwd}'"

                for (int i = 0; i < 100; i++) {
                    println "HEY-STEP ${i + 1}!!"
                    def s = _.sys.capture("pwd", callback)

                    if (!pwd.equals(s)) {
                        throw new RuntimeException("expecting '${pwd}', found: '${s}', step: ${i + 1}");
                    }
                }
        }
    })
}