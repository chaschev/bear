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

package bear.plugins.sh;

import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;
import bear.task.Task;
import bear.task.TaskDef;
import bear.vcs.CommandLineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class SystemEnvironmentPlugin extends Plugin<SystemSession, SystemEnvironmentPlugin.SystemSessionDef> {
    private static final Logger logger = LoggerFactory.getLogger(SystemEnvironmentPlugin.class);

    protected boolean sudo;
    private int defaultTimeout = 5000;
    private int singleTimeout = -1;

    public enum CopyCommandType {
        COPY, LINK, MOVE
    }

    protected SystemEnvironmentPlugin(GlobalContext global, String name) {
        super(global, new SystemSessionDef());
        taskDefMixin.setPlugin(this);
        this.name = name;
    }

    public static ConsoleCallback sshPassword(final SessionContext $) {
        String pass = $.var($.bear.sshPassword);
        return println(pass);
    }

    public static ConsoleCallback println(final String s) {
        return new ConsoleCallback() {
            @Override
            public void progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                if(buffer.contains("password")){
                    console.println(s);
                }
            }
        };
    }

    protected int getTimeout() {
        int r = singleTimeout == -1 ? defaultTimeout : singleTimeout;
        singleTimeout = -1;
        return r;
    }

    public SystemEnvironmentPlugin setDefaultTimeout(int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        return this;
    }

    public SystemEnvironmentPlugin setSingleTimeout(int singleTimeout) {
        this.singleTimeout = singleTimeout;
        return this;
    }

    public void connect() {

    }

    public boolean isSudo() {
        return sudo;
    }

    public static enum DownloadMethod {
        SCP, SFTP
    }

    public boolean isRemote() {
        throw new UnsupportedOperationException("todo");
    }



    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }

    @Override
    public abstract SystemSession newSession(SessionContext $, Task<TaskDef> parent);

    public static abstract class PackageManager {
        public abstract CommandLineResult installPackage(PackageInfo pi);
        public abstract CommandLineResult installPackage(String s);

        public abstract String command();
    }

    public static class SystemSessionDef extends TaskDef<SystemSession> {
        private SystemEnvironmentPlugin plugin;


        void setPlugin(SystemEnvironmentPlugin plugin) {
            this.plugin = plugin;
        }

        public SystemEnvironmentPlugin getPlugin() {
            return plugin;
        }

        @Override
        public SystemSession newSession(SessionContext $, final Task parent) {
            return plugin.newSession($, parent);
        }
    }


}
