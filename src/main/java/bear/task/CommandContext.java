package bear.task;

import bear.console.AbstractConsoleCommand;
import bear.core.SessionContext;
import bear.vcs.CommandLineResult;

public class CommandContext extends ExecContext<CommandContext>{
    public AbstractConsoleCommand<?> command;

    protected CommandLineResult<?> result;

    CommandContext(SessionContext $, ExecContext parent, AbstractConsoleCommand<?> command) {
        super($, parent);
        this.command = command;
    }

    @Override
    public boolean visit(TaskExecutionContext.ExecutionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return command.asText(false);
    }
}
