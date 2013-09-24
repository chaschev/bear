package cap4j.cli;

import cap4j.scm.CommandLineResult;
import cap4j.session.SystemEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
* User: chaschev
* Date: 9/24/13
*/
public class Script {
    public String cd = ".";

    protected SystemEnvironment system;

    public List<CommandLine> lines = new ArrayList<CommandLine>();

    public Script(SystemEnvironment system) {
        this.system = system;
    }

    public CommandLine line(){
        final CommandLine line = system.line();

        lines.add(line);

        return line;
    }

    public Script add(CommandLine commandLine) {
        lines.add(commandLine);

        return this;
    }

    public Script cd(String cd) {
        this.cd = cd;
        return this;
    }


    public CommandLineResult run() {
        return system.run(this);
    }
}
