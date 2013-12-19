package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CaptureBuilder extends CommandBuilder<CaptureBuilder> {
    String text;

    public CaptureBuilder(SessionContext $, String text) {
        super($);
        this.text = text;
    }

    @Override
    public CommandLine asLine() {
        return newLine(CommandLineResult.class).addRaw(text);
    }


}
