package bear.plugins.sh;

import bear.console.ConsoleCallback;
import bear.core.Bear;
import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;

import javax.annotation.Nonnull;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CommandInput<SELF extends CommandInput>{
    protected long timeoutMs = -1;
    protected boolean sudo;
    protected boolean force;
    protected boolean recursive = true;

    @Nonnull
    protected Optional<String> cd = Optional.absent();
    protected ConsoleCallback callback;

    CommandInput() {
    }

    public SELF sudo() {
        this.sudo = true;
        return self();
    }

    public SELF sudo(boolean b) {
        this.sudo = b;
        return self();
    }

    public SELF cd(String cd) {
        this.cd = Optional.of(cd);
        return self();
    }

    protected final SELF self(){
        return (SELF) this;
    }

    protected CommandLine<? extends CommandLineResult, ?> newLine(SessionContext $) {
        return newLine($, true);
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
            throw new Bear.ValidationException("sudo requires a callback!");
        }
    }

    protected boolean isTimeoutSet(){
        return timeoutMs != -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommandInput that = (CommandInput) o;

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
}
