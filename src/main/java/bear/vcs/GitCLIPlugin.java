/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.vcs;

import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.sh.CommandLine;
import bear.plugins.sh.ResultParser;
import bear.plugins.sh.Script;
import bear.plugins.sh.StubScript;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.*;
import chaschev.lang.OpenStringBuilder;
import com.google.common.base.Splitter;
import com.google.common.collect.PeekingIterator;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bear.session.Variables.equalTo;
import static bear.session.Variables.newVar;
import static chaschev.lang.LangUtils.elvis;
import static com.google.common.collect.Iterators.peekingIterator;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.addAll;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GitCLIPlugin extends VcsCLIPlugin<Task, TaskDef<Object, TaskResult>> {
    public static final ResultParser<LsResult>  LS_PARSER = new ResultParser<LsResult>() {
        @Override
        public LsResult parse(String script, String commandOutput) {
            return new LsResult(commandOutput, convertLsOutput(commandOutput));
        }
    };

    public static final LogParser LOG_PARSER = new LogParser();
    public static final Pattern HASH_REGEX = Pattern.compile("^[0-9a-f]{40}$");


    public final DynamicVariable<Integer> cloneDepth = newVar(-1);
    public final DynamicVariable<Boolean>
        enableSubmodules = newVar(false),
        submodulesRecursive = newVar(true),
        clean = equalTo(bear.clean)
    ;

    public final DynamicVariable<String> remote;

    public GitCLIPlugin(GlobalContext global) {
        super(global, new GitTaskDef());

        ((GitTaskDef) taskDefMixin).git = this;

        remote = Variables.<String>dynamic("remote user").setEqualTo(bear.vcsUsername);
    }

    @Override
    public GitCLIVCSSession newSession(SessionContext $, Task<Object, TaskResult> parent) {
        return new GitCLIVCSSession(parent, taskDefMixin, $);
    }



    /*
    Useful Git commands:

        git --no-pager log  --pretty=oneline|fuller --since="1 years 5 months"
        git --no-pager log -10 --all --date-order  <-- last 10 commits
        git --no-pager show 39c0b36725  <-- commit id
        git --no-pager diff master^^
        git --no-pager diff master~3^~2
        git [-follow] log  -- application.properties

        git branch -r # list remote branches
          andrey/HEAD -> andrey/master
          andrey/master
        mkdir git-demo

     Work cycle:

        cd git-demo
        git init
        git add.
        git commit -m «initial commit»
        git branch new-feature
        git checkout new-feature
        git add.
        git commit -m «Done with the new feature»
        git checkout master
        git diff HEAD new-feature
        git merge new-feature
        git branch -d new-feature
        git log --since=«1 day»

        Tracking remote branches (http://stackoverflow.com/a/1590791/1851024) = origin


     */

    public class GitCLIVCSSession extends VCSSession {
        public GitCLIVCSSession(Task<Object, TaskResult> parent, TaskDef def, SessionContext $) {
            super(parent, def, $);

            addDependency(new Dependency(taskDefMixin, "GIT", $, parent).addCommands("git --version"));
        }

        @Override
        public String head() {
            return elvis($(getBear().vcsBranchName), "HEAD");
        }

        public String origin() {
            return elvis($(remote), "origin");
        }

        @Override
        public String command() {
            return "git";
        }

        protected String verbose() {
            return $.var(getBear().verbose) ? "--verbose" : "";
        }

        @Override
        public VCSScript<?> checkout(String revision, String destination, Map<String, String> params) {
            String git = command();
            String remote = origin();

            List<String> args = new ArrayList<String>();

            if ($.isSet(bear.vcsBranchName)) {
                addAll(args, "-b", $(getBear().vcsBranchName));
            }

//            if (remoteIsNotOrigin(remote)) {
//                addAll(args, "-o", remote);
//            }

            if ($(cloneDepth) != -1) {
                addAll(args, "--depth", "" + $(cloneDepth));
            }

            final VCSScript script = new VCSScript($.sys, this);

            script
                .line()
                .stty().a(git).a("clone", verbose()).a(args)
                .a($(bear.repositoryURI), destination).build()
//                .line()
//                .stty()
//                .cd(destination)
//                .a(git, "checkout", "-b", "deploy", revision)
//                .build()
            ;

            syncSubmodules(git, script);

            return script;
        }

        private void syncSubmodules(String git, Script script) {
            if ($.isSet(enableSubmodules)) {
                script
                    .line().stty().a(git, "submodule", verbose(), "init").build()
                    .line().stty().a(git, "submodule", verbose(), "sync").build();

                if ($(submodulesRecursive)) {
                    script.line().addRaw("export GIT_RECURSIVE=$([ ! \"`#{git} --version`\" \\< \"git version 1.6.5\" ] && echo --recursive");
                } else {
                    script.line().stty().a(git, "submodule", verbose(), "update", "--init").build();
                }
            }
        }

        public VCSScript<?> sync(String revision, String destination, Map<String, String> params) {
            String git = command();
            String remote = origin();

            VCSScript<?> script = newVCSScript()
                .cd(destination);

            // Use git-config to setup a remote tracking branches. Could use
            // git-remote but it complains when a remote of the same name already
            // exists, git-config will just silently overwrite the setting every
            // time. This could cause weirdness in the remote cache if the url
            // changes between calls, but as long as the repositories are all
            // based from each other it should still work fine.

//            if (remoteIsNotOrigin(remote)) {
//                script
//                    .line().stty().a(git, "config", "remote." + remote + ".url", $(getBear().repositoryURI)).build()
//                    .line().stty().a(git, "config", "remote." + remote + ".fetch", "+refs/heads/*:refs/remotes/" + remote + "/*").build();
//            }

            return script
                .line().stty().a(git, "pull", verbose()).build();

            /*//since we're in a local branch already, just reset to specified revision rather than merge
            script
                .line().stty().a(git, "fetch", verbose(), remote).build()
                .line().stty().a(git, "fetch", "--tags", verbose(), remote).build()
                .line().stty().a(git, "reset", verbose(), "--hard", revision).build();

            syncSubmodules(git, script);

            // Make sure there's nothing else lying around in the repository (for
            // example, a submodule that has subsequently been removed).

            //todo think: in capistrano these commands chain

            if($(clean)){
                script.line().a("git", "clean", verbose()).addSplit("-d -x -f").build();
            }

            return script;*/
        }

        @Override
        public VCSScript<? extends BranchInfo> queryRevision(String revision) {
            return queryRevision(revision, emptyParams());
        }

        @Override
        public VCSScript<? extends BranchInfo> queryRevision(String revision, Map<String, String> params) {
            if (revision.startsWith("origin/")) {
                throw new IllegalArgumentException(String.format(
                    "Deploying remote branches is not supported.  Specify the remote branch as a local branch for the git repository you're deploying from (ie: '%s' rather than '%s').",
                    revision.replaceAll("origin/", ""),
                    revision)
                );
            }

            if (validRevision(revision)) {
                return newQueryRevisionResult(revision);
            }

            // this will break the logic a bit
            // queries remote repo to get latest commit revision
            // same as git --no-pager log -1 --all --date-order for local repo
            String newRevision = null;

            final LsResult lsResult = lsRemote(revision).timeoutSec(10).run();

            for (String s : lsResult.getFiles()) {
                final String rev = substringBefore(s, "\t");
                final String ref = substringAfter(s, "\t");

                if (ref.replaceFirst("refs/.*?/", "").trim().equals(revision)) {
                    newRevision = rev;
                }
            }

            if (validRevision(newRevision)) {
                return newQueryRevisionResult(newRevision);
            }

            //If sha is not found on remote, try expanding from local repository

            newRevision = $.sys.sendCommand(commandPrefix("rev-parse", emptyParams())
                .cd($(getBear().vcsBranchLocalPath))
                .a("--revs-only", origin() + "/" + revision)
                .timeoutSec(10)).output.trim();

            if (!validRevision(newRevision)) {
                return newQueryRevisionResult(newRevision);
            }

            throw new RuntimeException(String.format(
                "Unable to resolve revision for '%s' on repository '%s'.", revision, $(getBear().repositoryURI)));
        }

        @Override
        public ConsoleCallback passwordCallback() {
            final String password = $(getBear().vcsPassword);

            return new ConsoleCallback() {
                @Override
                @Nonnull
                public ConsoleCallbackResult progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                    if (buffer.matches(".*\\bpassword.*:.*")) {
                        console.println(password);
                    } else if (buffer.contains("(yes/no)")) {
                        // git is asking whether or not to connect
                        console.println("yes");
                    } else if (buffer.contains("passphrase")) {
                        // git is asking for the passphrase for the user's key
                        throw new UnsupportedOperationException("user prompt not yet supported!");
                    } else if (buffer.contains("accept (t)emporarily")) {
                        // git is asking whether to accept the certificate
                        console.println("t");
                    }

                    return ConsoleCallbackResult.CONTINUE;
                }
            };
        }


        private VCSScript<? extends BranchInfo> newQueryRevisionResult(String revision) {
            return new StubScript<BranchInfo>($.sys, this, new BranchInfo(null, revision, null));
        }

        @Override
        public VCSScript<?> export(String revision, String destination, Map<String, String> params) {
            VCSScript<?> checkout = checkout(revision, destination, emptyParams());
            checkout.add($.sys.rm(destination + "/.git").asLine());
            return checkout;
        }

        @Override
        public VCSScript<?> diff(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo: diff");
        }

        @Override
        public VCSScript<?> log(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo: log");
        }

        @Override
        public VCSScript<VcsLogInfo> logLastN(int n) {
            return newPlainScript("git --no-pager log -" + n + " --all --date-order", GitCLIPlugin.LOG_PARSER);
        }

        public VCSScript<LsResult> ls(String path, Map<String, String> params) {
            //readability on top
            //noinspection unchecked
            return newVCSScript(commandPrefix("ls", params, LsResult.class)
                .a(path)).setParser(LS_PARSER);
        }

        public VCSScript<LsResult> lsRemote(String revision) {
            //noinspection unchecked
            return newVCSScript(commandPrefix("ls-remote", emptyParams(), LsResult.class)
                .a($(getBear().repositoryURI), revision)).setParser(LS_PARSER);
        }

        private CommandLine<CommandLineResult, VCSScript<CommandLineResult>> commandPrefix(String cmd, Map<String, String> params) {
            return commandPrefix(cmd, params, CommandLineResult.class);
        }

        private <T extends CommandLineResult> CommandLine<T, VCSScript<T>> commandPrefix(String cmd, Map<String, String> params, Class<T> tClass) {
            return $.newCommandLine()
                .stty()
                .a(command(), cmd).p(params);
        }
    }

    @Override
    public InstallationTaskDef<InstallationTask> getInstall() {
        return new InstallationTaskDef<InstallationTask>(new SingleTaskSupplier<Object, TaskResult>() {
            @Override
            public InstallationTask<InstallationTaskDef> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<Object, TaskResult> def) {
                return new InstallationTask<InstallationTaskDef>(parent, (InstallationTaskDef) def, $) {
                    @Override
                    protected TaskResult exec(SessionRunner runner) {
                        return $.sys.getPackageManager().installPackage("git");
                    }

                    @Override
                    public Dependency asInstalledDependency() {
                        return new Dependency("git dep", $)
                            .addCommands("git --version");
                    }
                };
            }
        });
    }

    private static boolean validRevision(String revision) {
        return revision != null && HASH_REGEX.matcher(revision).matches();
    }

    private static boolean remoteIsNotOrigin(String remote) {
        return !remote.equals("origin");
    }

    static class GitTaskDef extends TaskDef<Object, TaskResult> {
        private GitCLIPlugin git;

        GitTaskDef() {
            super(new NamedSupplier<Object, TaskResult>("git.session", new SingleTaskSupplier<Object, TaskResult>() {
                @Override
                public Task<Object, TaskResult> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<Object, TaskResult> def) {
                    return def.singleTaskSupplier().createNewSession($, parent, def);
                }
            }));
        }
    }

    private static List<String> convertLsOutput(String s) {
        return newArrayList(s.split("\n"));
    }

    static class LogParser implements ResultParser<VcsLogInfo> {
        public static final DateTimeFormatter GIT_DATE_FORMAT = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss yyyy Z");

        @Override
        public VcsLogInfo parse(String script, String s) {
            List<VcsLogInfo.LogEntry> entries = new ArrayList<VcsLogInfo.LogEntry>();

            OpenStringBuilder comment = new OpenStringBuilder(256);

            for (PeekingIterator<String> it = peekingIterator(Splitter.on("\n").trimResults().split(s).iterator()); it.hasNext(); ) {
                String line;

                String revision = null;

                for(line = it.next();it.hasNext();line=it.next()){
                    if(line.startsWith("commit")) {
                        revision = substringAfter(line, " ").trim();
                        Matcher matcher = HASH_REGEX.matcher(revision);
                        if(matcher.matches()){
                            break;
                        }
                    }
                }

                if(revision == null) break;

                while(it.hasNext() && !it.peek().contains("Author: ")) it.next();
                String author = substringAfter(it.next(), "Author: ");
                String stringDate = substringAfter(it.next(), "Date: ").trim();
                DateTime date = DateTime.parse(stringDate, GIT_DATE_FORMAT);

                it.next(); //empty line

                comment.setLength(0);

                while (it.hasNext()) {
                    line = it.peek();
                    String possibleRevision = substringAfter(line, " ");

                    if (HASH_REGEX.matcher(possibleRevision).matches()) {
                        break;
                    }

                    comment.append(it.next()).append("\n");
                }

                comment.trim();

                entries.add(new VcsLogInfo.LogEntry(date, author, comment.toString(), revision));
            }

            return new VcsLogInfo(s, entries);

        }
    }


}
