package cap4j.scm;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.session.GenericUnixRemoteEnvironment;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public abstract class VcsCLI {
    protected SessionContext ctx;
    protected GlobalContext global;
    protected CapConstants cap;

    protected VcsCLI(SessionContext ctx, GlobalContext global) {
        this.ctx = ctx;
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

    public GenericUnixRemoteEnvironment.SshSession.WithSession runCallback() {
        return new GenericUnixRemoteEnvironment.SshSession.WithSession() {
            @Override
            public void act(Session session, Session.Shell shell) throws Exception {
                if(text.contains("password")){
                    System.out.println(text);
                    final OutputStream os = session.getOutputStream();
                    os.write((ctx.var(cap.vcsPassword) + "\n").getBytes(IOUtils.UTF8));
                    os.flush();
                }
            }
        };
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

    public static class Script{
        public String cd = ".";

        public List<CommandLine> lines = new ArrayList<CommandLine>();

        public Script add(CommandLine commandLine) {
            lines.add(commandLine);

            return this;
        }

        public Script cd(String cd) {
            this.cd = cd;
            return this;
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
