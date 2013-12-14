package bear.plugins.misc;

import bear.core.SessionContext;
import bear.plugins.sh.CommandLine;
import com.google.common.base.Preconditions;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class StartStopBuilder {

    private final CommandLine line;

    String userWithGroup;
    String pidPath;
    boolean inBackground = true;
    private String execPath;

    String extra;


    public StartStopBuilder(SessionContext $) {
        line = $.sys.line().addRaw("start-stop-daemon");
    }

    public StartStopBuilder withPidFile(String path){
        pidPath = path;
        return this;
    }

    public StartStopBuilder asUser(String user){
        return asUser(user, user);
    }

    public StartStopBuilder asUser(String user, String group){
        userWithGroup = user + ":" + group;
        return this;

        //--exec ${HOME}/target/universal/stage/bin/${APP} --background --start -- -Dconfig.resource=$CONFIG -Dhttp.port=$PORT -Dhttp.address=$ADDRESS $EXTRA
    }

    public StartStopBuilder execPath(String path){
        this.execPath = path;

        return this;
    }

    public StartStopBuilder noBackground(){
        inBackground = false;
        return this;
    }

    public StartStopBuilder addExtra(String extra){
        this.extra = extra;
        return this;
    }

    public CommandLine build(){
        Preconditions.checkNotNull(execPath, "executable path is null");

        if(pidPath != null){
            line.addRaw(" --pidfile ").a(pidPath);
        }

        if(userWithGroup != null){
            line.addRaw(" --chuid ").a(userWithGroup);
        }

        line.addRaw(" " + execPath);

        if(inBackground){
            line.addRaw(" --background");
        }

        line.addRaw(" --start -- ");

        if(extra != null){
            line.addRaw(extra);
        }

        return line;
    }

    public static StartStopBuilder newBuilder(SessionContext $){
        return new StartStopBuilder($);
    }
}
