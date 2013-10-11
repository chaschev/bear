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

import bear.ssh.MyStreamCopier;
import chaschev.util.Exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class AbstractConsole extends bear.console.AbstractConsole.Terminal {
    public static abstract class Listener {
        protected AbstractConsole console;

        protected abstract void textAdded(String text, MarkedBuffer buffer) throws Exception;
    }

    Listener listener;

    OutputStream out;

    /**
     * There are basically two copiers: for stdout and stderr which copy everything to out.
     */
    List<MyStreamCopier> copiers = new ArrayList<MyStreamCopier>();
    List<MarkedBuffer> buffers = new ArrayList<MarkedBuffer>();
    List<Future> futures = new ArrayList<Future>();

    protected AbstractConsole(Listener listener) {
        this.listener = listener;
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

    public AbstractConsole addInputStream(InputStream is, boolean stdErr) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final MyStreamCopier copier = new MyStreamCopier(is, baos, stdErr);
        final MarkedBuffer buffer = new MarkedBuffer(stdErr);

        copiers.add(copier);
        buffers.add(buffer);

        copier.listener(new MyStreamCopier.Listener() {
            @Override
            public void reportProgress(long transferred, byte[] buf, int read) throws Exception {
                if (buf == null) {
                    buffer.progress(baos.toByteArray());

                    listener.textAdded(buffer.interimText(), buffer);

                    buffer.markInterim();
                }
            }
        });

        return this;
    }

    public void stopStreamCopiers() {
        for (int i = 0; i < copiers.size(); i++) {
            copiers.get(i).stop();
            final Future future = futures.get(i);

            if (!future.isDone()) {
                future.cancel(true);
            }

        }
    }

    public boolean awaitStreamCopiers(long duration, TimeUnit unit) {
        long period = duration / 9;

        if (period == 0) {
            period = 1;
        }

        long sleepMs = unit.toMillis(period);
        long sleepNano = unit.toNanos(period) - 1000000 * sleepMs;

        final long durationMs = unit.toMillis(duration);

        long startedAt = System.currentTimeMillis();

        while (true) {
            try {
                boolean allFinished = true;

                for (MyStreamCopier copier : copiers) {
                    if (!copier.isFinished()) {
                        allFinished = false;
                        break;
                    }
                }

                if (allFinished) {
                    return true;
                }

                final long now = System.currentTimeMillis();

                if (now - startedAt > durationMs) {
                    return false;
                }

                Thread.sleep(sleepMs, (int) sleepNano);
            } catch (InterruptedException e) {
                throw Exceptions.runtime(e);
            }
        }
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
            futures.add(copier.spawn(service));
        }

        return this;
    }

    public StringBuilder concatOutputs() {
        StringBuilder sb = new StringBuilder(buffers.get(0).length() + 10);

        for (int i = 0; i < buffers.size(); i++) {
            MarkedBuffer buffer = buffers.get(i);

            sb.append(buffer.wholeText());

            if (i != buffers.size() - 1) {
                return sb.append("\n");
            }
        }

        return sb;
    }

}
