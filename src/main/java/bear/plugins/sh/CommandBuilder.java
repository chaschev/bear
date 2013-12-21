package bear.plugins.sh;

import bear.console.ConsoleCallback;
import bear.context.HavingContext;
import bear.core.SessionContext;
import bear.core.except.NoSuchFileException;
import bear.core.except.PermissionsException;
import bear.core.except.ValidationException;
import bear.core.except.WrongCommandException;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkArgument;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class CommandBuilder<SELF extends CommandBuilder> extends HavingContext<SELF, SessionContext> implements ResultValidator  {
    private volatile boolean builderMethodCalled;

    protected long timeoutMs = -1;
    protected boolean sudo;
    protected boolean force;
    protected boolean recursive = true;

    protected ResultValidator validator = this;

    @Nonnull
    protected Optional<String> cd = Optional.absent();
    protected ConsoleCallback callback;

    CommandBuilder() {
        super(null);
    }

    CommandBuilder(SessionContext $) {
        super($);
    }

    public SELF sudo() {
        this.sudo = true;
        return self();
    }

    public SELF sudo(boolean b) {
        this.sudo = b;
        return self();
    }

    public CommandLineResult run(){
        builderMethodCalled = true;

        CommandLine line = asLine();

        return $.sys.sendCommand(line);
    }

    public SELF inDir(String cd) {
        checkArgument(".".equals(cd) && !Strings.isNullOrEmpty(cd), "wrong cd: %s", cd);

        this.cd = Optional.of(cd);
        return self();
    }

    protected final SELF self(){
        return (SELF) this;
    }

    protected CommandLine<? extends CommandLineResult, ?> newLine(SessionContext $) {
        return newLine($, true);
    }

    protected <T extends CommandLineResult> CommandLine<T, Script> newLine(Class<T> tClass) {
        return (CommandLine<T, Script>) newLine($, true);
    }

    protected CommandLine<? extends CommandLineResult, ?> newLine(SessionContext $, boolean useSshCallback){
        CommandLine line = $.sys.script().line();

        if(isTimeoutSet()){
            line.timeoutMs(timeoutMs);
        }

        return forLine(line, $, useSshCallback);
    }

    protected CommandLine<? extends CommandLineResult, ?> forLine(CommandLine line, SessionContext $) {
        return forLine(line, $, true);
    }

    protected CommandLine<? extends CommandLineResult, ?> forLine(CommandLine line, SessionContext $, boolean useSshCallback) {
        if(cd.isPresent()){
            line.cd(cd.get());
        }

        if(useSshCallback && sudo && callback == null){
            callback = $.sshCallback();
        }

        if(sudo){
            line.sudo();
        }else
        if(callback != null){
            line.stty();
        }

        line.setValidator(this);

        return line;
    }

    public SELF callback(ConsoleCallback callback) {
        this.callback = callback;
        return self();
    }

    public void validate(){

    }

    public void validateBeforeSend(){
        if(sudo && callback == null){
            throw new ValidationException("sudo requires a callback!");
        }
    }

    protected boolean isTimeoutSet(){
        return timeoutMs != -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommandBuilder that = (CommandBuilder) o;

        if (sudo != that.sudo) return false;
        if (callback != null ? !callback.equals(that.callback) : that.callback != null) return false;
        if (!cd.equals(that.cd)) return false;

        return true;
    }

    //todo might need to hide this
    public SELF nonRecursive() {
        recursive = false;
        return self();
    }

    @Override
    public int hashCode() {
        int result = (sudo ? 1 : 0);
        result = 31 * result + cd.hashCode();
        result = 31 * result + (callback != null ? callback.hashCode() : 0);
        return result;
    }

    public SELF timeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;

        return self();
    }

    public SELF timeoutSec(int timeoutSec) {
        return timeoutMs(1000 * timeoutSec);
    }

    public SELF timeoutMin(int timeoutMin) {
        return timeoutSec(60 * timeoutMin);
    }

    public SELF force() {
        force = true;
        return self();
    }

    public CommandLine asLine(){
        builderMethodCalled = true;
        return null;
    }

    @Override
    protected final void finalize() throws Throwable {
        if(!builderMethodCalled){
            String s = null;

            try {
                CommandLine line = asLine();

                if(line != null){
                    s = line.asText(false);
                }else{
                    s = toString();
                }
            } catch (UnsupportedOperationException e) {
                s = toString();
            }

            SessionContext.logger.error("command had been created but was not used: " + s);
        }

        super.finalize();
    }

    public SELF dontValidate(){
        validator = null;
        return self();
    }

    public SELF validateResult(ResultValidator validator){
        this.validator = validator;
        return self();
    }

    @Override
    public void validate(String script, String output) {
        if(validator != this && validator != null) {
            validator.validate(script, output);
        }else{
            //todo reflectize?
            ValidationException.checkLine("Permission denied", script, output, PermissionsException.class);
            ValidationException.checkLine("bash: command not found", script, output, WrongCommandException.class);
            ValidationException.checkLine("such file or directory", script, output, NoSuchFileException.class);
        }
    }

    protected void setCalled(){
        this.builderMethodCalled = true;
    }
}
