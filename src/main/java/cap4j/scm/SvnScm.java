package cap4j.scm;

import cap4j.GlobalContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cap4j.GlobalContext.gvars;
import static cap4j.VariableName.*;
import static cap4j.scm.SvnScm.CommandLine.commandLine;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public class SvnScm extends BaseScm {
    public static class CommandLine{
        List<String> strings = new ArrayList<String>(4);

        public CommandLine a(String... s){
            Collections.addAll(strings, s);
            return this;
        }

        public static CommandLine commandLine(String... s){
            return new CommandLine().a(s);
        }

        public CommandLine p(Map<String, String> params) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                strings.add(" --" + e.getKey() + "=" + e.getValue() + " ");
            }
            return this;
        }
    }

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
        //scm :switch, arguments, verbose, authentication, "-r#{revision}", repository, destination
        return commandPrefix("switch", params)
            .a("-r" + revision,
                scmRepository(),
                destination);
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
        return commandLine(
            command(),
            svnCmd
        ).p(params)
            .a(auth());
    }

    private static String scmRepository() {
        return GlobalContext.var(scmRepository, (String) null);
    }

    protected String[] auth(){
        final String user = gvars().getString(scmUsername, null);
        final String pw = gvars().getString(scmPassword, null);
        final boolean preferPrompt = gvars().get(scmPreferPrompt, true);
        final boolean authCache = gvars().get(scmAuthCache, true);

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
