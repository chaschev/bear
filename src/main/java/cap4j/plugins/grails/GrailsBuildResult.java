package cap4j.plugins.grails;

import cap4j.session.Result;

/**
* User: achaschev
* Date: 8/3/13
* Time: 11:26 PM
*/
public class GrailsBuildResult {
    public Result result;
    public String path;

    public GrailsBuildResult(Result result, String path) {
        this.result = result;
        this.path = path;
    }
}
