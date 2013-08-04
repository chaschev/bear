package cap4j.scm;

import cap4j.CapConstants;
import cap4j.VarContext;
import com.google.common.base.Function;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cap4j.GlobalContext.var;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public class SvnVcs extends Vcs {

    public SvnVcs(VarContext ctx) {
        super(ctx);
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
        final CommandLine<BranchInfoResult> r = commandPrefix("info", params)
            .a("-r" + revision,
                scmRepository())
            .cd(ctx.var(CapConstants.releasePath))
            .setParser(new Function<String, BranchInfoResult>() {
                public BranchInfoResult apply(String s) {
                    return new BranchInfoResult(
                        StringUtils.substringBetween(s, "Last Changed Author: ", "\n").trim(),
                        StringUtils.substringBetween(s, "Revision: ", "\n").trim(),
                        StringUtils.substringBetween(s, "Last Changed Date: ", "\n".trim())
                    );
                }
            });

        return r;
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

    private CommandLine commandPrefix(String svnCmd, Map<String, String> params) {
        return ctx.newCommandLine()
            .stty()
            .a(command(), svnCmd).p(params)
            .a(auth());
    }

    private String scmRepository() {
        return ctx.var(CapConstants.vcsBranchURI);
    }

    protected String[] auth(){
        final String user = var(CapConstants.vcsUserName, null);
        final String pw = var(CapConstants.vcsPassword, null);
        final boolean preferPrompt = var(CapConstants.scmPreferPrompt, true);
        final boolean authCache = var(CapConstants.scmAuthCache, true);

        List<String> r = new ArrayList<String>(4);

        if(user == null) return r.toArray(new String[0]);

        r.add("--username " + user);

        if(!preferPrompt && !authCache){
            r.add("--password " + pw);
        }

        if(authCache){
            r.add("--no-auth-cache");
        }

        return r.toArray(new String[r.size()]);
    }
}
