/*
 * Copyright 2010-2012 sshj contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cap4j.ssh;

import cap4j.session.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class MyStreamCopier {

    private long periodMs;
    private int periodNano;
    private volatile boolean finished;

    public boolean isStdErr;
    private long count;

    public interface Listener {
        void reportProgress(long transferred, byte[] buf, int read)
                throws Exception;

    }

    private static final Listener NULL_LISTENER = new Listener() {
        @Override
        public void reportProgress(long transferred, byte[] buf, int read) {
        }
    };

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final InputStream in;
    private final OutputStream out;

    private Listener listener = NULL_LISTENER;

    private int bufSize = 1024;
    private boolean keepFlushing = true;
    private final long length = -1;

    public MyStreamCopier(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public MyStreamCopier(InputStream in, OutputStream out, boolean stdErr) {
        this.in = in;
        this.out = out;
        isStdErr = stdErr;
    }

    public MyStreamCopier bufSize(int bufSize) {
        this.bufSize = bufSize;
        return this;
    }

    public MyStreamCopier keepFlushing(boolean keepFlushing) {
        this.keepFlushing = keepFlushing;
        return this;
    }

    public MyStreamCopier listener(Listener listener) {
        if (listener == null) listener = NULL_LISTENER;
        this.listener = listener;
        return this;
    }

    protected volatile boolean stopFlag;

    public MyStreamCopier stop() {
        this.stopFlag = true;
        return this;
    }

    public Future<Result> spawn(ExecutorService service) {
        return service.submit(new Callable<Result>() {
            @Override
            public Result call() {
                while (!stopFlag) {
                    try {
                        nonBlockingCopy();

                        listener.reportProgress(count, null, -1);

                        if(!(periodMs <= 0 && periodNano <=0)){
                            Thread.sleep(periodMs, periodNano);
                        }
                    } catch (Exception e) {
                        log.error("", e);
                        return Result.ERROR;
                    }
                }

                finished = true;

                return Result.OK;
            }
        });
    }

    public long nonBlockingCopy() throws Exception {
        final byte[] buf = new byte[bufSize];
        count = 0;
        int read;
        int avaliable;

        while ((avaliable = in.available()) > 0 && (read = in.read(buf, 0, Math.min(avaliable, buf.length))) != -1){
            if(read > 0){
                count = write(buf, count, read);
            }
        }

        if (!keepFlushing){
            out.flush();
        }

        if (length != -1 && read == -1)
            throw new IOException("Encountered EOF, could not transfer " + length + " bytes");

        return count;
    }

    private long write(byte[] buf, long count, int read) throws Exception {
        out.write(buf, 0, read);
        count += read;
        if (keepFlushing)
            out.flush();
        listener.reportProgress(count, buf, read);
        return count;
    }


    public InputStream getIn() {
        return in;
    }

    public OutputStream getOut() {
        return out;
    }

    public void setPeriodMs(long periodMs) {
        this.periodMs = periodMs;
    }

    public void setPeriodNano(int periodNano) {
        this.periodNano = periodNano;
    }


    public boolean isFinished() {
        return finished;
    }
}
