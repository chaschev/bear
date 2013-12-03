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

import bear.cli.CommandLine;
import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.Shell;
import bear.session.ProcessRunner;
import bear.session.Result;
import bear.task.Task;
import bear.task.TaskDef;
import bear.vcs.CommandLineResult;
import bear.vcs.LocalCommandLine;
import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.ZipFileSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Shell("sh")
public class GenericUnixLocalEnvironmentPlugin extends SystemEnvironmentPlugin {
    private static final Logger logger = LoggerFactory.getLogger(GenericUnixLocalEnvironmentPlugin.class);

    public GenericUnixLocalEnvironmentPlugin(GlobalContext global) {
        super(global, "local unix plugin");
        this.shell = new ShShellMode(this, cmdAnnotation());
    }

    @Override
    public SystemSession newSession(SessionContext $, Task<TaskDef> parent) {
        return new SystemSession(parent, taskDefMixin, $) {
            {
                this.address = $.address;
            }

            @Override
            public List<String> ls(String path) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.ls");
            }

            /**
             * Single files a treated in a different way.
             *
             * @param dest
             * @param paths
             */
            @Override
            public void zip(String dest, Collection<String> paths) {
                final Zip zip = new Zip();

                zip.setCompress(false);
                zip.setDestFile(new File(dest));
                zip.setProject(new Project());

                if (paths.size() != 1) {
                    for (String path : paths) {
                        final ZipFileSet set = new ZipFileSet();

                        final File file = new File(path);

                        set.setDir(file.getParentFile());
                        set.setIncludes(file.getName());

                        zip.addZipfileset(set);
                    }
                } else {
                    final ZipFileSet set = new ZipFileSet();

                    final File toAdd = new File(paths.iterator().next());

                    if (toAdd.isDirectory()) {
                        set.setDir(toAdd);
                    } else {
                        set.setFile(toAdd);
                    }

                    zip.addZipfileset(set);
                }

                zip.execute();
            }

            @Override
            public void unzip(String file, @Nullable String destDir) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.unzip");
            }

            @Override
            public String newTempDir() {
                final File bearDir = new File(FileUtils.getTempDirectory(), "bear");

                if (bearDir.exists()) {
                    try {
                        FileUtils.deleteDirectory(bearDir);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                Preconditions.checkArgument(bearDir.mkdir(), "could not create temp dir");

                return bearDir.getAbsolutePath();
            }

            @Override
            public boolean isUnix() {
                return true;
            }

            @Override
            public boolean isNativeUnix() {
                return SystemUtils.IS_OS_UNIX;
            }

            @Override
            public Result scp(String dest, String[] args, String... paths) {
                throw new UnsupportedOperationException("todo .scp");
            }

            @Override
            public boolean isRemote() {
                return false;
            }

            @Override
            public <T extends CommandLineResult> CommandLine<T, ?> newCommandLine(Class<T> aClass) {
                return new LocalCommandLine<T>(this);
            }

            @Override
            public Result download(List<String> paths, DownloadMethod method, File destParentDir) {
                return Result.ERROR;
            }

            @Override
            public <T extends CommandLineResult> T sendCommandImpl(final AbstractConsoleCommand<T> command, ConsoleCallback userCallback) {

                logger.debug("command: {}", command);

                final ProcessRunner.ProcessResult r = new ProcessRunner<T>(command, global.localExecutor)
                    .setInputCallback(userCallback)
                    .setProcessTimeoutMs((int) command.getTimeoutMs())
                    .run();

                if (r.exitCode == -1) {
                    return (T) new CommandLineResult(r.text, Result.ERROR);
                }

                final T t = ((CommandLine<T, ?>)command).parseResult($, r.text);

                t.result = Result.OK;

                return t;
            }

            @Override
            public Result sftp(String dest, String host, String path, String user, String pw) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.sftp");
            }

            @Override
            public Result upload(String dest, File... files) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.scpLocal");
            }

            @Override
            public Result mkdirs(String... dirs) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.mkdirs");
            }

            @Override
            protected Result copyOperation(String src, String dest, CopyCommandType type, boolean folder, String owner) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.copyOperation");
            }

            @Override
            public Result chown(String user, boolean recursive, String... dest) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.chown");
            }

            @Override
            public Result chmod(String octal, boolean recursive, String... files) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.chmod");
            }

            @Override
            public Result writeString(String path, String s) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.writeString");
            }

            @Override
            public Result writeStringAs(WriteStringInput input) {
                throw new UnsupportedOperationException("todo .writeStringAs");
            }

            @Override
            public String readString(String path, String _default) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.readString");
            }

            @Override
            public boolean exists(String path) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.exists");
            }

            @Override
            public String readLink(String path) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.readLink");
            }

            @Override
            public Result rmCd(String dir, String... paths) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.rm");
            }

            @Override
            public CommandLine rmLineImpl(@Nonnull String dir, CommandLine line, String... paths) {
                throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.rmLine");
            }

            @Override
            public String getAddress() {
                return "localhost";
            }
        };
    }


}
