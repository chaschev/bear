package cap4j.scm;

import cap4j.cli.CommandLine;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public class SvnVcsCLI extends VcsCLI {

    public SvnVcsCLI(SessionContext ctx, GlobalContext global) {
        super(ctx, global);
    }

    @Override
    public String head(){
        return "HEAD";
    }

    @Override
    public String command() {
        return "svn";
    }

    @Override
    public CommandLine checkout(String revision, String destination, Map<String, String> params) {
        return commandPrefix("checkout", params)
         .a("-r" + revision,
             scmRepository(),
             destination);
    }

    @Override
    public CommandLine sync(String revision, String destination, Map<String, String> params) {
        return commandPrefix("switch", params)
            .a("-r" + revision,
                scmRepository(),
                destination);
    }

    @Override
    public CommandLine<BranchInfoResult> queryRevision(String revision) {
        return queryRevision(revision, emptyParams());
    }

    @Override
    public CommandLine<BranchInfoResult> queryRevision(String revision, Map<String, String> params) {

        return commandPrefix("info", params)
            .a("-r" + revision,
                scmRepository())
            .cd(ctx.var(cap.releasePath))
            .setParser(new Function<String, BranchInfoResult>() {
                public BranchInfoResult apply(String s) {
                    return new BranchInfoResult(
                        StringUtils.substringBetween(s, "Last Changed Author: ", "\n").trim(),
                        StringUtils.substringBetween(s, "Revision: ", "\n").trim(),
                        StringUtils.substringBetween(s, "Last Changed Date: ", "\n".trim())
                    );
                }
            });
    }

    @Override
    public CommandLine export(String revision, String destination, Map<String, String> params) {
        return commandPrefix("export", params)
            .a("-r" + revision,
                scmRepository(),
                destination);
    }

    @Override
    public CommandLine diff(String rFrom, String rTo, Map<String, String> params) {
        return commandPrefix("diff", params)
            .a("-r" + rFrom + ":" + rTo);
    }

    @Override
    public CommandLine log(String rFrom, String rTo, Map<String, String> params) {
        return commandPrefix("log", params)
            .a("-r" + rFrom + ":" + rTo);
    }

    public static final class LsResult extends CommandLineResult{
        List<String> files;

        public LsResult(String text, List<String> files) {
            super(text);
            this.files = files;
        }

        public List<String> getFiles() {
            return files;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("LsResult{");
            sb.append("files=").append(files);
            sb.append('}');
            return sb.toString();
        }
    }

    public CommandLine<LsResult> ls(String path, Map<String, String> params){
        return commandPrefix("ls", params)
            .a(path).setParser(new Function<String, LsResult>() {
                public LsResult apply(String s) {
                    return new LsResult(s, Lists.newArrayList(s.split("\n")));
                }
            });
    }

    private CommandLine commandPrefix(String svnCmd, Map<String, String> params) {
        return ctx.newCommandLine()
            .stty()
            .a(command(), svnCmd).p(params)
            .a(auth());
    }

    private String scmRepository() {
        return ctx.var(cap.vcsBranchURI);
    }

    protected String[] auth(){
        final String user = global.var(cap.vcsUsername, null);
        final String pw = global.var(cap.vcsPassword, null);
        final boolean preferPrompt = global.var(cap.scmPreferPrompt, false);
        final boolean authCache = global.var(cap.scmAuthCache, false);

        List<String> r = new ArrayList<String>(4);

        if(user == null) return r.toArray(new String[0]);

        r.add("--username");
        r.add(user);

        if(!preferPrompt && !authCache){
            r.add("--password");
            r.add(pw);
        }

        if(authCache){
            r.add("--no-auth-cache");
        }

        return r.toArray(new String[r.size()]);
    }
}
