package cap4j.plugins.grails;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.Plugin;
import cap4j.session.DynamicVariable;
import cap4j.session.VariableUtils;
import com.google.common.base.Function;

import static cap4j.core.CapConstants.*;
import static cap4j.session.VariableUtils.condition;
import static cap4j.session.VariableUtils.isSet;
import static cap4j.session.VariableUtils.joinPath;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:28 PM
*/
public class GrailsPlugin extends Plugin {
    public final DynamicVariable<String>
        homePath = dynamic("homePath", "Grails root dir"),
        grailsBin = joinPath("grailsBin", homePath, "bin"),
        projectPath = dynamicNotSet("projectPath", "Project root dir"),
        grailsExecName = CapConstants.dynamic("grailsExec", "grails or grails.bat", new Function<SessionContext, String>() {
            public String apply(SessionContext ctx) {
                return "grails" + (ctx.system.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = condition("grailsExecPath", isSet(null, homePath),
            joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = strVar("warName", "i.e. ROOT.war").defaultTo("ROOT.war"),
        projectWarPath = joinPath("projectWarPath", projectPath, warName),
        releaseWarPath = condition("releaseWarPath", cap.isRemoteEnv, joinPath(cap.releasePath, warName), projectWarPath);

    public final DynamicVariable<Boolean>
        grailsClean = VariableUtils.eql("grailsClean", cap.clean).setDesc("clean project")
    ;

    public GrailsPlugin(GlobalContext global) {
        super(global);
    }

    //            projectPath,
//            clean
}
