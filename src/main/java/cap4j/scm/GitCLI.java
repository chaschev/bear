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

package cap4j.scm;

import cap4j.cli.CommandLine;
import cap4j.cli.Script;
import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.session.DynamicVariable;
import cap4j.session.GenericUnixRemoteEnvironment;
import cap4j.session.Variables;
import cap4j.task.InstallationTask;
import com.google.common.base.Function;
import net.schmizz.sshj.common.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cap4j.session.Variables.newVar;
import static com.chaschev.chutils.util.LangUtils.elvis;
import static com.google.common.collect.Lists.*;
import static java.util.Collections.addAll;


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GitCLI extends VcsCLI<GitCLI> {
    public final DynamicVariable<Integer> cloneDepth = newVar(-1);
    public final DynamicVariable<Boolean>
        enableSubmodules = newVar(false),
        submodulesRecursive = newVar(true);

    public final DynamicVariable<String> remote;

    public GitCLI(GlobalContext global) {
        super(global);
        remote = Variables.<String>dynamic("remote user").setEqualTo(cap.vcsUsername);
    }

    @Override
    public GitCLISession newSession(SessionContext $) {
        return new GitCLISession($);
    }

    public class GitCLISession extends Session<GitCLI> {
        public GitCLISession(SessionContext $) {
            super($);
        }

        @Override
        public String head() {
            return elvis($(cap.vcsBranchName), "HEAD");
        }

        public String origin() {
            return elvis($(remote), "origin");
        }

        @Override
        public String command() {
            return "git";
        }

        protected String verbose() {
            return $.var(cap.verbose) ? "--verbose" : "";
        }

        @Override
        public Script checkout(String revision, String destination, Map<String, String> params) {
            String git = command();
            String remote = origin();

            List<String> args = new ArrayList<String>();

            if ($.isSet(cap.vcsBranchName)) {
                addAll(args, "-b", $(cap.vcsBranchURI));
            }

            if (remoteIsNotOrigin(remote)) {
                addAll(args, "-o", remote);
            }

            if ($(cloneDepth) != -1) {
                addAll(args, "--depth", "" + $(cloneDepth));
            }

            final Script script = $.system.script();

            script
                .line()
                .stty().a(git).a("clone", verbose()).a(args)
                .a($(cap.repositoryURI), destination).build()
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


        public Script sync(String revision, String destination, Map<String, String> params) {
            String git = command();
            String remote = origin();

            Script script = $.system.script()
                .cd(destination);

            // Use git-config to setup a remote tracking branches. Could use
            // git-remote but it complains when a remote of the same name already
            // exists, git-config will just silently overwrite the setting every
            // time. This could cause weirdness in the remote cache if the url
            // changes between calls, but as long as the repositories are all
            // based from each other it should still work fine.

            if (remoteIsNotOrigin(remote)) {
                script
                    .line().stty().a(git, "config", "remote." + remote + ".url", $(cap.repositoryURI)).build()
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
        public Script<BranchInfoResult> queryRevision(String revision) {
            return queryRevision(revision, emptyParams());
        }

        @Override
        public Script<BranchInfoResult> queryRevision(String revision, Map<String, String> params) {
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

            final SvnCLI.LsResult lsResult = $.system.run(lsRemote(revision).timeoutSec(10), passwordCallback());

            for (String s : lsResult.getFiles()) {
                final String rev = StringUtils.substringBefore(s, "|");
                final String ref = StringUtils.substringAfter(s, "|");

                if (ref.replaceFirst("refs/.*?/", "").trim().equals(revision)) {
                    newRevision = rev;
                }
            }

            if (validRevision(newRevision)) {
                return newQueryRevisionResult(newRevision);
            }

            //If sha is not found on remote, try expanding from local repository

            newRevision = $.system.run(commandPrefix("rev-parse", emptyParams())
                .a("--revs-only", origin() + "/" + revision)
                .timeoutSec(10), passwordCallback()).text.trim();

            if (!validRevision(newRevision)) {
                return newQueryRevisionResult(newRevision);
            }

            throw new RuntimeException(String.format(
                "Unable to resolve revision for '%s' on repository '%s'.", revision, $(cap.repositoryURI)));
        }

        @Override
        public GenericUnixRemoteEnvironment.SshSession.WithSession passwordCallback() {
            final String password = $(cap.vcsPassword);

            return new GenericUnixRemoteEnvironment.SshSession.WithSession(null, null) {
                @Override
                public void act(net.schmizz.sshj.connection.channel.direct.Session session, net.schmizz.sshj.connection.channel.direct.Session.Shell shell) throws Exception {
                    if (text.matches(".*\\bpassword.*:.*")) {
                        answer(session, password);
                    } else if (text.contains("(yes/no)")) {
                        // git is asking whether or not to connect
                        answer(session, "yes");
                    } else if (text.contains("passphrase")) {
                        // git is asking for the passphrase for the user's key
                        throw new UnsupportedOperationException("user prompt not yet supported!");
                    } else if (text.contains("accept (t)emporarily")) {
                        // git is asking whether to accept the certificate
                        answer(session, "t");
                    }
                }
            };
        }


        private Script.StubScript<BranchInfoResult> newQueryRevisionResult(String revision) {
            return new Script.StubScript<BranchInfoResult>($.system, new BranchInfoResult(null, revision, null));
        }


        @Override
        public Script export(String revision, String destination, Map<String, String> params) {
            return checkout(revision, destination, emptyParams())
                .line($.system.rmLine(".", destination + "/.git"));
        }

        @Override
        public CommandLine diff(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo: diff");
        }

        @Override
        public CommandLine log(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo: log");
        }

        public CommandLine<SvnCLI.LsResult> ls(String path, Map<String, String> params) {
            //noinspection unchecked
            return commandPrefix("ls", params)
                .a(path).setParser(new Function<String, SvnCLI.LsResult>() {
                    public SvnCLI.LsResult apply(String s) {
                        return new SvnCLI.LsResult(s, convertLsOutput(s));
                    }
                });
        }

        public CommandLine<SvnCLI.LsResult> lsRemote(String revision) {
            //noinspection unchecked
            return commandPrefix("ls-remote", emptyParams())
                .a($(cap.repositoryURI), revision).setParser(new Function<String, SvnCLI.LsResult>() {
                    public SvnCLI.LsResult apply(String s) {
                        return new SvnCLI.LsResult(s, convertLsOutput(s));
                    }
                });
        }

        private List<String> convertLsOutput(String s) {
            return newArrayList(transform(
                partition(newArrayList(s.split("\n")), 2), new Function<List<String>, String>() {
                public String apply(List<String> input) {
                    String r = input.get(0);
                    if (input.size() > 1) {
                        r += ":" + input.get(1);
                    }

                    return r;
                }
            }));
        }

        private CommandLine commandPrefix(String cmd, Map<String, String> params) {
            return $.newCommandLine()
                .stty()
                .a(command(), cmd).p(params);
        }

        private String scmRepository() {
            return $(cap.vcsBranchURI);
        }
    }

    @Override
    public InstallationTask getSetup() {
        return InstallationTask.nop();
    }

    public static void answer(net.schmizz.sshj.connection.channel.direct.Session session, String what) throws IOException {
        final OutputStream os = session.getOutputStream();
        os.write((what + "\n").getBytes(IOUtils.UTF8));
        os.flush();
    }

    private static boolean validRevision(String revision) {
        return revision != null && revision.matches("^[0-9a-f]{40}$");
    }

    private static boolean remoteIsNotOrigin(String remote) {
        return !remote.equals("origin");
    }
}
