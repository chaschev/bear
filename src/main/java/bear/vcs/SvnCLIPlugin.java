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
import bear.core.SessionContext;
import bear.core.GlobalContext;
import bear.task.InstallationTaskDef;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.Dependency;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SvnCLIPlugin extends VcsCLIPlugin<Task, TaskDef<?>> {
    protected SvnCLIPlugin(GlobalContext global, SvnTaskDef taskDef) {
        super(global, taskDef);

        ((SvnTaskDef) taskDefMixin).svn = this;
    }

    @Override
    public SvnCLISession newSession(SessionContext $, Task<TaskDef> parent) {
        return new SvnCLISession(parent, taskDefMixin, $);
    }

    public class SvnCLISession extends Session {
        protected SvnCLISession(Task<TaskDef> parent, TaskDef def, SessionContext $) {
            super(parent, def, $);

            addDependency(new Dependency(taskDefMixin, "SVN", $, parent).addCommands("svn --version"));
        }

        @Override
        public String head() {
            return "HEAD";
        }

        @Override
        public String command() {
            return "svn";
        }

        @Override
        public Script checkout(String revision, String destination, Map<String, String> params) {

            return $.sys.script().line(commandPrefix("checkout", params)
                .a("-r" + revision,
                    vcsRepository(),
                    destination));
        }

        @Override
        public Script sync(String revision, String destination, Map<String, String> params) {
            return $.sys.script().line(commandPrefix("switch", params)
                .a("-r" + revision,
                    vcsRepository(),
                    destination));
        }

        @Override
        public Script<BranchInfoResult> queryRevision(String revision) {
            return queryRevision(revision, emptyParams());
        }

        @SuppressWarnings("unchecked")
        @Override
        public Script<BranchInfoResult> queryRevision(String revision, Map<String, String> params) {
            return $.sys.script().line(commandPrefix("info", params)
                .a("-r" + revision,
                    vcsRepository())
                .cd($(bear.releasePath))
            ).setParser(new Function<String, BranchInfoResult>() {
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
        public Script export(String revision, String destination, Map<String, String> params) {
            return $.sys.script().line(commandPrefix("export", params)
                .a("-r" + revision,
                    vcsRepository(),
                    destination));
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


        public CommandLine<LsResult> ls(String path, Map<String, String> params) {
            return commandPrefix("ls", params)
                .a(path).setParser(new Function<String, LsResult>() {
                    public LsResult apply(String s) {
                        return new LsResult(s, Lists.newArrayList(s.split("\n")));
                    }
                });
        }

        private CommandLine commandPrefix(String svnCmd, Map<String, String> params) {
            return $.newCommandLine()
                .stty()
                .a(command(), svnCmd).p(params)
                .a(auth());
        }

        private String vcsRepository() {
            return $(bear.vcsBranchURI);
        }

        protected String[] auth() {
            final String user = global.var(bear.vcsUsername, null);
            final String pw = global.var(bear.vcsPassword, null);
            final boolean preferPrompt = global.var(bear.vcsPreferPrompt, false);
            final boolean authCache = global.var(bear.vcsAuthCache, false);

            List<String> r = new ArrayList<String>(4);

            if (user == null) return r.toArray(new String[0]);

            r.add("--username");
            r.add(user);

            if (!preferPrompt && !authCache) {
                r.add("--password");
                r.add(pw);
            }

            if (authCache) {
                r.add("--no-auth-cache");
            }

            return r.toArray(new String[r.size()]);
        }
    }

    @Override
    public InstallationTaskDef getInstall() {
        return InstallationTaskDef.EMPTY;
    }

    public static final class LsResult extends CommandLineResult {
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

    static class SvnTaskDef extends TaskDef<SvnCLISession> {
        private SvnCLIPlugin svn;


        @Override
        public SvnCLISession newSession(SessionContext $, final Task parent) {
            return svn.newSession($, parent);
        }
    }
}
