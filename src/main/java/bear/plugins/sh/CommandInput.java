package bear.plugins.sh;

import bear.console.ConsoleCallback;
import bear.core.Bear;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CommandInput<SELF extends CommandInput>{
    protected boolean sudo;
    protected String cd;
    protected ConsoleCallback callback;

    CommandInput() {
    }

    public SELF sudo() {
        this.sudo = true;
        return self();
    }

    public SELF cd(String cd) {
        this.cd = cd;
        return self();
    }

    protected SELF self(){
        return (SELF) this;
    }

    public CommandInput<SELF> callback(ConsoleCallback callback) {
        this.callback = callback;
        return this;
    }

    public void validate(){

    }

    public void validateBeforeSend(){
        if(sudo && callback == null){
            throw new Bear.ValidationException("sudo requires a callback!");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommandInput that = (CommandInput) o;

        if (sudo != that.sudo) return false;
        if (callback != null ? !callback.equals(that.callback) : that.callback != null) return false;
        if (cd != null ? !cd.equals(that.cd) : that.cd != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (sudo ? 1 : 0);
        result = 31 * result + (cd != null ? cd.hashCode() : 0);
        result = 31 * result + (callback != null ? callback.hashCode() : 0);
        return result;
    }
}
