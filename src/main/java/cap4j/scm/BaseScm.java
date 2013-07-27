package cap4j.scm;

import cap4j.GlobalContext;
import cap4j.VariableName;

import java.util.Map;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public abstract class BaseScm {
    public SvnScm.CommandLine checkout(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public SvnScm.CommandLine sync(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public SvnScm.CommandLine export(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public SvnScm.CommandLine diff(String rFrom, String rTo, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    /**
     * f the given revision represents a "real" revision, this should
     * simply return the revision value. If it represends a pseudo-revision
     * (like Subversions "HEAD" identifier), it should yield a string
     * containing the commands that, when executed will return a string
     * that this method can then extract the real revision from.
     */
    public void queryRevision(String revision,Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public String nextRevision(String r){
        return r;
    }

    public abstract String command();

    public SvnScm.CommandLine log(String rFrom, String rTo, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }
}
