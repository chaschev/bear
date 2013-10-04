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
import bear.console.ConsoleCallback;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.session.SystemEnvironment;
import bear.session.Variables;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;
import bear.cli.Script;

import java.util.Collections;
import java.util.Map;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class VcsCLIPlugin<T extends VcsCLIPlugin> extends Plugin {

    protected VcsCLIPlugin(GlobalContext global) {
        super(global);
    }

    @Override
    public void initPlugin() {
        String msg = Variables.checkSet(global.localCtx, this.getClass().getSimpleName(),
            bear.repositoryURI
//            cap.vcsBranchName - no, because it's set in the script
        );

        if(msg != null){
            global.localCtx.log("%s", msg);
            throw new RuntimeException(msg);
        }
    }

    public static Map<String, String> emptyParams() {
        return Collections.emptyMap();
    }

    public abstract Session newSession(SessionContext $);

    public abstract class Session extends Task {
        protected Session(TaskDef parent, SessionContext $) {
            super(parent, $);
        }

        @Override
        protected TaskResult run(TaskRunner runner) {
            throw new UnsupportedOperationException("VCS task cannot be run");
        }

        public Script checkout(String revision, String destination, Map<String, String> params) {
            throw new UnsupportedOperationException("todo");
        }

        public Script sync(String revision, String destination, Map<String, String> params) {
            throw new UnsupportedOperationException("todo");
        }

        public Script export(String revision, String destination, Map<String, String> params) {
            throw new UnsupportedOperationException("todo");
        }

        public CommandLine diff(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo");
        }

        public Script<BranchInfoResult> queryRevision(String revision) {
            return queryRevision(revision, emptyParams());
        }



        /**
         * f the given revision represents a "real" revision, this should
         * simply return the revision value. If it represends a pseudo-revision
         * (like Subversions "HEAD" identifier), it should yield a string
         * containing the commands that, when executed will return a string
         * that this method can then extract the real revision from.
         */
        public Script<BranchInfoResult> queryRevision(String revision, Map<String, String> params) {
            throw new UnsupportedOperationException("todo");
        }

        public String nextRevision(String r) {
            return r;
        }

        public String command() {
            throw new UnsupportedOperationException("todo VcsCLIContext.command");
        }

        public CommandLine log(String rFrom, String rTo, Map<String, String> params) {
            throw new UnsupportedOperationException("todo");
        }

        public CommandLine ls(String path, Map<String, String> params) {
            throw new UnsupportedOperationException("todo");
        }

        public abstract String head();

        public ConsoleCallback passwordCallback() {
            return SystemEnvironment.passwordCallback($.var(bear.vcsPassword));
        }

        public CommandLine<SvnCLIPlugin.LsResult> ls(String path) {
            return ls(path, emptyParams());
        }


        public <T> T $(DynamicVariable<T> varName) {
            return $.var(varName);
        }
    }

    public static class StringResult extends CommandLineResult {
        public String value;

        public StringResult(String text, String value) {
            super(text);

            this.value = value;
        }
    }

    public static class CommandLineOperator {
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
