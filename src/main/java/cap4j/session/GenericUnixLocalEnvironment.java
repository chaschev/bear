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

package cap4j.session;

import cap4j.core.GlobalContext;
import cap4j.cli.CommandLine;
import cap4j.scm.CommandLineResult;
import cap4j.scm.LocalCommandLine;
import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.ZipFileSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class GenericUnixLocalEnvironment extends SystemEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(GenericUnixLocalEnvironment.class);

    public GenericUnixLocalEnvironment(String name, GlobalContext global) {
        super(name, global);
    }

    public GenericUnixLocalEnvironment(String name, String desc, GlobalContext global) {
        super(name, desc, global);
    }

    @Override
    public List<String> ls(String path) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.ls");
    }

    /**
     * Single files a treated in a different way.
     * @param dest
     * @param paths
     */
    @Override
    public void zip(String dest, Collection<String> paths) {
        final Zip zip = new Zip();

        zip.setCompress(false);
        zip.setDestFile(new File(dest));
        zip.setProject(new Project());

        if(paths.size() != 1){
        for (String path : paths) {
            final ZipFileSet set = new ZipFileSet();

            final File file = new File(path);

            set.setDir(file.getParentFile());
            set.setIncludes(file.getName());

            zip.addZipfileset(set);
        }
        }else{
            final ZipFileSet set = new ZipFileSet();

            final File toAdd = new File(paths.iterator().next());

            if(toAdd.isDirectory()){
                set.setDir(toAdd);
            }else{
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
        final File cap4jDir = new File(FileUtils.getTempDirectory(), "cap4j");

        if(cap4jDir.exists()){
            try {
                FileUtils.deleteDirectory(cap4jDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Preconditions.checkArgument(cap4jDir.mkdir(), "could not create temp dir");

        return cap4jDir.getAbsolutePath();
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
    public <T extends CommandLineResult> CommandLine<T> newCommandLine(Class<T> aClass) {
        return new LocalCommandLine<T>();
    }

    @Override
    public Result download(List<String> paths, DownloadMethod method, File destParentDir) {
        return Result.OK;
    }

    public static class ProcessRunner<T extends CommandLineResult> {
        ExecutorService executor;
        CommandLine<T> line;

        int processTimeoutMs = 60000;
        private GenericUnixRemoteEnvironment.SshSession.WithSession inputCallback;

        public ProcessRunner(CommandLine<T> line, ExecutorService executor) {
            this.line = line;
            this.executor = executor;
        }

        public ProcessRunner<T> setInputCallback(GenericUnixRemoteEnvironment.SshSession.WithSession inputCallback) {
            this.inputCallback = inputCallback;
            return this;
        }

        public GenericUnixRemoteEnvironment.SshSession.WithSession getInputCallback() {
            return inputCallback;
        }

        public static class ProcessResult {
            public int exitCode;
            public String text;

            public ProcessResult(int exitCode, String text) {
                this.exitCode = exitCode;
                this.text = text;
            }
        }

        public ProcessRunner<T> setProcessTimeoutMs(int processTimeoutMs) {
            this.processTimeoutMs = processTimeoutMs;
            return this;
        }

        public ProcessResult run() {
            Process process = null;
            final StringBuffer sb = new StringBuffer(1024);

            try {
//                process = new ProcessBuilder(line.strings).directory(new File(line.cd)).start();
                process = new ProcessBuilder("svn", "ls")
                    .directory(new File("c:\\Users\\achaschev\\prj\\atocha"))
                    .redirectErrorStream(true)
                    .redirectOutput(new File("c:\\users\\achaschev\\temp.txt"))
                    .start();



                final InputStream is = process.getInputStream();
                final OutputStream os = process.getOutputStream();

                final long startedAt = System.currentTimeMillis();

                final boolean[] processFinished = {false};

                final Process finalProcess = process;

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        long now = -1;
                        while (true) {
                            now = System.currentTimeMillis();
                            try {
                                Thread.sleep(50);

                                int lengthBefore = sb.length();

                                while (is.available() > 0) {
                                    sb.append((char) is.read());
                                }

                                if (sb.length() != lengthBefore) {
                                    System.out.print(sb.subSequence(lengthBefore, sb.length()));
                                }

                                if (processFinished[0] || now - startedAt > processTimeoutMs) {
                                    break;
                                }

                            } catch (Exception e) {
                                logger.info("", e);
                                break;
                            }
                        }

                        if (now - startedAt > processTimeoutMs) {
                            finalProcess.destroy();
                        }
                    }
                });

                final int exitCode = process.waitFor();

                processFinished[0] = true;

                //wait while output pipes to our streams
                Thread.sleep(100);

                while (is.available() > 0) {
                    sb.append((char) is.read());
                }

//                sb.append(IOUtils.toString(is));
//                sb.append(IOUtils.toString(es));

                return new ProcessResult(exitCode, sb.toString());
            } catch (Exception e) {
                logger.info("", e);
                return new ProcessResult(-1, sb.toString());
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    @Override
    public <T extends CommandLineResult> T run(CommandLine<T> line, final GenericUnixRemoteEnvironment.SshSession.WithSession inputCallback) {
        logger.debug("command: {}", line);

        final ProcessRunner.ProcessResult r = new ProcessRunner<T>(line, global.localExecutor)
            .setInputCallback(inputCallback)
            .setProcessTimeoutMs(line.timeoutMs)
            .run();

        if (r.exitCode == -1) {
            return (T) new CommandLineResult(r.text, Result.ERROR);
        }

        final T t = line.parseResult(r.text);

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
}
