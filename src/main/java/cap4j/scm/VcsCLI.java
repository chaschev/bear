package cap4j.scm;

import cap4j.cli.CommandLine;
import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.session.GenericUnixRemoteEnvironment;
import cap4j.session.SystemEnvironment;

import java.util.Collections;
import java.util.Map;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public abstract class VcsCLI {
    protected SessionContext $;
    protected GlobalContext global;
    protected CapConstants cap;

    protected VcsCLI(SessionContext $, GlobalContext global) {
        this.$ = $;
        this.global = global;
        this.cap = global.cap;
    }

    public CommandLine checkout(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public CommandLine sync(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public CommandLine export(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public CommandLine diff(String rFrom, String rTo, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public CommandLine<BranchInfoResult> queryRevision(String revision){
        return queryRevision(revision, emptyParams());
    }

    public static Map<String, String> emptyParams() {
        return Collections.emptyMap();
    }

    /**
     * f the given revision represents a "real" revision, this should
     * simply return the revision value. If it represends a pseudo-revision
     * (like Subversions "HEAD" identifier), it should yield a string
     * containing the commands that, when executed will return a string
     * that this method can then extract the real revision from.
     */
    public CommandLine<BranchInfoResult> queryRevision(String revision, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public String nextRevision(String r){
        return r;
    }

    public abstract String command();

    public CommandLine log(String rFrom, String rTo, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public CommandLine ls(String path, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public abstract String head();

    public GenericUnixRemoteEnvironment.SshSession.WithSession passwordCallback() {
        return SystemEnvironment.passwordCallback($.var(cap.vcsPassword));
    }

    public CommandLine<SvnVcsCLI.LsResult> ls(String path){
        return ls(path, emptyParams());
    }

    public static class StringResult extends CommandLineResult{
        public String value;

        public StringResult(String text, String value) {
            super(text);

            this.value = value;
        }
    }

    public static class CommandLineOperator{
        String s;

        public CommandLineOperator(String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }
    }

}
