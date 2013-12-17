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

package bear.core;

import bear.console.ConsoleCallbackResult;
import bear.console.ConsoleCallbackResultType;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.ssh.MyStreamCopier;
import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class AbstractConsole extends bear.console.AbstractConsole.Terminal {
    private static final Logger logger = LoggerFactory.getLogger(GenericUnixRemoteEnvironmentPlugin.RemoteConsole.class);

    public static abstract class Listener {
        protected AbstractConsole console;

        @Nonnull
        protected abstract ConsoleCallbackResult textAdded(String textAdded, MarkedBuffer buffer) throws Exception;
    }

    Listener listener;

    OutputStream out;

    Closeable shutdownTrigger;

    /**
     * There are basically two copiers: for stdout and stderr which copy everything to out.
     */
    List<MyStreamCopier> copiers = new ArrayList<MyStreamCopier>();
    List<MarkedBuffer> buffers = new ArrayList<MarkedBuffer>();
    List<Future> futures = new ArrayList<Future>();

    protected volatile boolean finished = false;

    protected volatile transient ConsoleCallbackResult lastCallbackResult;
    protected volatile transient ConsoleCallbackResult lastError;


    protected AbstractConsole(Listener listener, Closeable shutdownTrigger) {
        this.listener = listener;
        this.shutdownTrigger = shutdownTrigger;
        listener.console = this;
    }

    public void println(String s) {
        print(s + "\n");
    }

    public void print(String s) {
        try {
            out.write(s.getBytes());
            out.flush();
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public AbstractConsole addInputStream(InputStream is) {
        return addInputStream(is, false);
    }

    public static final class OpenBAOS extends ByteArrayOutputStream{
        public byte[] getBuffer(){
            return buf;
        }

        public int getLength(){
            return count;
        }
    }

    public AbstractConsole addInputStream(InputStream is, boolean stdErr) {
        final OpenBAOS baos = new OpenBAOS();
        final MyStreamCopier copier = new MyStreamCopier(is, baos, stdErr);
        final MarkedBuffer buffer = new MarkedBuffer(stdErr);

        copiers.add(copier);
        buffers.add(buffer);

        copier.listener(new MyStreamCopier.Listener() {
            @Override
            public void reportProgress(long transferred, byte[] buf, int read) throws Exception {
                synchronized (baos){
                    buffer.progress(baos.getBuffer(), baos.getLength());
                }

                LoggerFactory.getLogger("log").trace("appended to buffer: {}", buffer.interimText());

                lastCallbackResult = listener.textAdded(buffer.interimText(), buffer);

                if(lastCallbackResult.type == ConsoleCallbackResultType.EXCEPTION){
                    lastError = lastCallbackResult;
//                    PlayPlugin.logger.debug("OOOOOOOOOOOOPS - set error!!");
                }

                switch (lastCallbackResult.type) {
                    case DONE:
                    case EXCEPTION:
                    case FINISHED:
                        stopStreamCopiersGracefully();
                        IOUtils.closeQuietly(shutdownTrigger);
                        break;
                }



                buffer.markInterim();
            }
        });

        return this;
    }

    @Override
    public void finishWithResult(ConsoleCallbackResult callbackResult){
        lastCallbackResult = callbackResult;
        stopStreamCopiersGracefully();
        IOUtils.closeQuietly(shutdownTrigger);

        if(callbackResult.type.isError()){
            lastError = callbackResult;
        }
    }

    @Override
    public boolean isDone(){
        return finished;
    }

    public void stopStreamCopiersGracefully() {
//        logger.debug("OOOOOOOOOOOOPS - stopStreamCopiersGracefully");

        finished = true;

        for (MyStreamCopier copier : copiers) {
            copier.stop();
        }
    }

    public void stopStreamCopiers() {
//        logger.debug("OOOOOOOOOOOOPS - stopStreamCopiers", new Exception());

        for (int i = 0; i < copiers.size(); i++) {
            copiers.get(i).stop();
            final Future future = futures.get(i);

            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    public boolean awaitStreamCopiers(long duration, TimeUnit unit) {
//        logger.debug("OOOOOOOOOOOOPS - awaitStreamCopiers");

        long periodNs = NANOSECONDS.convert(duration, unit) / 9;

        if (periodNs == 0) {
            periodNs = 1;
        }

        long sleepMs = MILLISECONDS.convert(periodNs, NANOSECONDS);
        long sleepNano = periodNs - NANOSECONDS.convert(sleepMs, MILLISECONDS);

        final long durationMs = unit.toMillis(duration);

        long startedAt = System.currentTimeMillis();

        for (MyStreamCopier copier : copiers) {
            copier.setFinishAtMs(startedAt + durationMs);
        }

        while (true) {
            try {
                for (MyStreamCopier copier : copiers) {
                    copier.triggerCopy();
                }

                final long now = System.currentTimeMillis();

                long timeElapsedMs = now - startedAt;

                if (allFinished()) {
                    return true;
                }

                if (timeElapsedMs > durationMs) {
                    return false;
                }

                Thread.sleep(sleepMs, (int) sleepNano);
            } catch (InterruptedException e) {
                throw Exceptions.runtime(e);
            }
        }
    }

    public boolean allFinished() {
        boolean allFinished = true;

        for (MyStreamCopier copier : copiers) {
            if (!copier.isFinished()) {
                allFinished = false;
                break;
            }
        }
        return allFinished;
    }

    public void setOut(OutputStream out) {
        this.out = out;
    }


    public AbstractConsole bufSize(int size) {
        for (MyStreamCopier copier : copiers) {
            copier.bufSize(size);
        }

        return this;
    }


    public AbstractConsole spawn(ExecutorService service) {
        for (MyStreamCopier copier : copiers) {
            futures.add(copier.spawn(service, -1));
        }

        return this;
    }

    public AbstractConsole spawn(ExecutorService service, int timeout, TimeUnit unit) {
        for (MyStreamCopier copier : copiers) {
            futures.add(copier.spawn(service, System.currentTimeMillis() + unit.toMillis(timeout)));
        }

        return this;
    }

    public StringBuilder concatOutputs() {
        StringBuilder sb = new StringBuilder(buffers.get(0).length() + 20);

        for (int i = 0; i < buffers.size(); i++) {
            MarkedBuffer buffer = buffers.get(i);

            sb.append(buffer.wholeText());

            if (i != buffers.size() - 1) {
                return sb.append("\n");
            }
        }

        return sb;
    }

    public Optional<ConsoleCallbackResult> getLastCallbackResult() {
        return Optional.fromNullable(lastCallbackResult);
    }

    public Optional<ConsoleCallbackResult> getLastError() {
        return Optional.fromNullable(lastError);
    }
}
