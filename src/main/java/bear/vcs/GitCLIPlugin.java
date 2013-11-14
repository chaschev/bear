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

import bear.cli.CommandLine;
import bear.cli.Script;
import bear.cli.StubScript;
import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.Dependency;
import bear.task.InstallationTaskDef;
import bear.task.Task;
import bear.task.TaskDef;
import com.google.common.base.Function;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static chaschev.lang.LangUtils.elvis;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.addAll;


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GitCLIPlugin extends VcsCLIPlugin<Task, TaskDef<?>> {
    public final DynamicVariable<Integer> cloneDepth = Variables.newVar(-1);
    public final DynamicVariable<Boolean>
        enableSubmodules = Variables.newVar(false),
        submodulesRecursive = Variables.newVar(true);

    public final DynamicVariable<String> remote;

    public GitCLIPlugin(GlobalContext global) {
        super(global, new GitTaskDef());

        ((GitTaskDef)taskDefMixin).git = this;

        remote = Variables.<String>dynamic("remote user").setEqualTo(bear.vcsUsername);
    }

    @Override
    public GitCLIVCSSession newSession(SessionContext $, Task<TaskDef> parent) {
        return $.wire(new GitCLIVCSSession(parent, taskDefMixin, $));
    }

    public class GitCLIVCSSession extends VCSSession {
        public GitCLIVCSSession(Task<TaskDef> parent, TaskDef def, SessionContext $) {
            super(parent, def, $);

            addDependency(new Dependency(taskDefMixin, "GIT", $, parent).addCommands("git --version"));
        }

        @Override
        public String head() {
            return elvis($(bear.vcsBranchName), "HEAD");
        }

        public String origin() {
            return elvis($(remote), "origin");
        }

        @Override
        public String command() {
            return "git";
        }

        protected String verbose() {
            return $.var(bear.verbose) ? "--verbose" : "";
        }

        @Override
        public VCSScript<?> checkout(String revision, String destination, Map<String, String> params) {
            String git = command();
            String remote = origin();

            List<String> args = new ArrayList<String>();

            if ($.isSet(bear.vcsBranchName)) {
                addAll(args, "-b", $(bear.vcsBranchURI));
            }

            if (remoteIsNotOrigin(remote)) {
                addAll(args, "-o", remote);
            }

            if ($(cloneDepth) != -1) {
                addAll(args, "--depth", "" + $(cloneDepth));
            }

            final VCSScript script = new VCSScript($.sys, this);

            script
                .line()
                .stty().a(git).a("clone", verbose()).a(args)
                .a($(bear.repositoryURI), destination).build()
                .line()
                .stty()
                .cd(destination)
                .a(git, "checkout", "-b", "deploy", revision).build();

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

            if (remoteIsNotOrigin(remote)) {
                script
                    .line().stty().a(git, "config", "remote." + remote + ".url", $(bear.repositoryURI)).build()
                    .line().stty().a(git, "config", "remote." + remote + ".fetch", "+refs/heads/*:refs/remotes/" + remote + "/*").build();
            }

            //since we're in a local branch already, just reset to specified revision rather than merge
            script
                .line().stty().a(git, "fetch", verbose(), remote).build()
                .line().stty().a(git, "fetch", "--tags", verbose(), remote).build()
                .line().stty().a(git, "reset", verbose(), "--hard", revision).build();

            syncSubmodules(git, script);

            // Make sure there's nothing else lying around in the repository (for
            // example, a submodule that has subsequently been removed).

            //todo think in capistrano these commands chain

            return script.line().a("git", "clean", verbose()).addSplit("-d -x -f").build();
        }

        @Override
        public VCSScript<? extends BranchInfoResult> queryRevision(String revision) {
            return queryRevision(revision, emptyParams());
        }

        @Override
        public VCSScript<? extends BranchInfoResult> queryRevision(String revision, Map<String, String> params) {
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

            //this will break the logic a bit
            String newRevision = null;

            final LsResult lsResult = $.sys.run(
                lsRemote(revision).timeoutSec(10), passwordCallback());

            for (String s : lsResult.getFiles()) {
                final String rev = StringUtils.substringBefore(s, "\t");
                final String ref = StringUtils.substringAfter(s, "\t");

                if (ref.replaceFirst("refs/.*?/", "").trim().equals(revision)) {
                    newRevision = rev;
                }
            }

            if (validRevision(newRevision)) {
                return newQueryRevisionResult(newRevision);
            }

            //If sha is not found on remote, try expanding from local repository

            newRevision = $.sys.sendCommand(commandPrefix("rev-parse", emptyParams())
                .cd($(bear.vcsBranchLocalPath))
                .a("--revs-only", origin() + "/" + revision)
                .timeoutSec(10), passwordCallback()).text.trim();

            if (!validRevision(newRevision)) {
                return newQueryRevisionResult(newRevision);
            }

            throw new RuntimeException(String.format(
                "Unable to resolve revision for '%s' on repository '%s'.", revision, $(bear.repositoryURI)));
        }

        @Override
        public ConsoleCallback passwordCallback() {
            final String password = $(bear.vcsPassword);

            return new ConsoleCallback() {
                @Override
                public void progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
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
                }
            };
        }


        private VCSScript<? extends BranchInfoResult> newQueryRevisionResult(String revision) {
            return new StubScript<BranchInfoResult>($.sys, this, new BranchInfoResult(null, revision, null));
        }

        @Override
        public VCSScript<?> export(String revision, String destination, Map<String, String> params) {
            return checkout(revision, destination, emptyParams())
                .line($.sys.rmLine($.sys.line(), ".", destination + "/.git"));
        }

        @Override
        public VCSScript<?> diff(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo: diff");
        }

        @Override
        public VCSScript<?> log(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo: log");
        }

        public VCSScript<LsResult> ls(String path, Map<String, String> params) {
            //readability on top
            //noinspection unchecked
            return newVCSScript(commandPrefix("ls", params, LsResult.class)
                .a(path)).setParser(new Function<String, LsResult>() {
                public LsResult apply(String s) {
                    return new LsResult(s, convertLsOutput(s));
                }
            });
        }

        public VCSScript<LsResult> lsRemote(String revision) {
            //noinspection unchecked
            return newVCSScript(commandPrefix("ls-remote", emptyParams(), LsResult.class)
                .a($(bear.repositoryURI), revision)).setParser(new Function<String, LsResult>() {
                public LsResult apply(String s) {
                    return new LsResult(s, convertLsOutput(s));
                }
            });
        }

        private List<String> convertLsOutput(String s) {
            return newArrayList(s.split("\n"));
        }

        private CommandLine<CommandLineResult, VCSScript<CommandLineResult>> commandPrefix(String cmd, Map<String, String> params) {
            return commandPrefix(cmd, params, CommandLineResult.class);
        }

        private <T extends CommandLineResult >CommandLine<T, VCSScript<T>> commandPrefix(String cmd, Map<String, String> params, Class<T> tClass) {
            return $.newCommandLine()
                .stty()
                .a(command(), cmd).p(params);
        }
    }

    @Override
    public InstallationTaskDef getInstall() {
        return InstallationTaskDef.EMPTY;
    }

    private static boolean validRevision(String revision) {
        return revision != null && revision.matches("^[0-9a-f]{40}$");
    }

    private static boolean remoteIsNotOrigin(String remote) {
        return !remote.equals("origin");
    }

    static class GitTaskDef extends TaskDef<GitCLIVCSSession> {
        private GitCLIPlugin git;


        @Override
        public GitCLIVCSSession newSession(SessionContext $, final Task parent) {
            return git.newSession($, parent);
        }
    }
}
