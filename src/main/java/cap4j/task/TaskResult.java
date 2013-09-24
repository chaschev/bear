package cap4j.task;

import cap4j.scm.CommandLineResult;
import cap4j.session.Result;

/**
* User: chaschev
* Date: 7/27/13
*/
public class TaskResult {
    public Result result;
    public CommandLineResult cliResult;

    public TaskResult(CommandLineResult cliResult) {
        this.cliResult = cliResult;
        this.result = cliResult.result;
    }

    public TaskResult(Result result) {
        this.result = result;
    }
}
