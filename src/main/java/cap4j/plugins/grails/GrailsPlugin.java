package cap4j.plugins.grails;

import cap4j.CapConstants;
import cap4j.VarContext;
import cap4j.session.DynamicVariable;
import cap4j.session.VariableUtils;
import com.google.common.base.Function;

import static cap4j.CapConstants.*;
import static cap4j.session.VariableUtils.condition;
import static cap4j.session.VariableUtils.isSet;
import static cap4j.session.VariableUtils.joinPath;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:28 PM
*/
public class GrailsPlugin {
    public static final DynamicVariable<String>
        grailsPath = dynamic("grailsPath", "Grails root dir"),
        grailsBin = joinPath("grailsBin", grailsPath, "bin"),
        projectPath = dynamicNotSet("projectPath", "Project root dir"),
        grailsExecName = CapConstants.dynamic("grailsExec", "grails or grails.bat", new Function<VarContext, String>() {
            public String apply(VarContext varContext) {
                return "grails" + (varContext.system.isNativeUnix() ? "" : ".bat");
            }
        }),
        grailsExecPath = condition("grailsExecPath", isSet(null, grailsPath),
            joinPath(grailsBin, grailsExecName), grailsExecName),
        warName = strVar("warName", "i.e. ROOT.war").defaultTo("ROOT.war"),
        projectWarPath = joinPath("projectWarPath", projectPath, warName),
        releaseWarPath = condition("releaseWarPath", isRemoteEnv, joinPath(releasePath, warName), projectWarPath);

    public static final DynamicVariable<Boolean>
        grailsClean = VariableUtils.eql("grailsClean", CapConstants.clean).setDesc("clean project")
    ;
//            projectPath,
//            clean
}
