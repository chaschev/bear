package cap4j.scm;

import cap4j.session.Result;

/**
* User: achaschev
* Date: 8/4/13
*/
public class CommandLineResult {
    public String text;
    public Result result;
    public int exitStatus;

    public CommandLineResult() {
    }

    public CommandLineResult(String text) {
        this.text = text;
    }

    public CommandLineResult(String text, Result result) {
        this.text = text;
        this.result = result;
    }
}
