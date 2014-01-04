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

package bear.session;

import bear.plugins.sh.CommandLine;
import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.vcs.CommandLineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ProcessRunner<T extends CommandLineResult<?>> {
    private static final Logger logger = LoggerFactory.getLogger(ProcessRunner.class);

    ExecutorService executor;
    CommandLine<T, ?> line;

    int processTimeoutMs = 60000;
    private ConsoleCallback inputCallback;

    public ProcessRunner(AbstractConsoleCommand<T> line, ExecutorService executor) {
        this.line = (CommandLine<T, ?>) line;
        this.executor = executor;
    }

    public ProcessRunner<T> setInputCallback(ConsoleCallback inputCallback) {
        this.inputCallback = inputCallback;
        return this;
    }

    public ConsoleCallback getInputCallback() {
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
