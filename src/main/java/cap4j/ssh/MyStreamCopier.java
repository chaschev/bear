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
import net.schmizz.concurrent.Event;
import net.schmizz.concurrent.ExceptionChainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class MyStreamCopier {

    public interface Listener {

        void reportProgress(long transferred)
                throws IOException;

    }

    private static final Listener NULL_LISTENER = new Listener() {
        @Override
        public void reportProgress(long transferred) {
        }
    };

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final InputStream in;
    private final OutputStream out;

    private Listener listener = NULL_LISTENER;

    private int bufSize = 1;
    private boolean keepFlushing = true;
    private final long length = -1;

    public MyStreamCopier(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
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
        final Future<Result> submit = service.submit(new Callable<Result>() {
            @Override
            public Result call() {
                while (!stopFlag) {
                    try {
                        nonBlockingCopy();
                    } catch (Exception e) {
                        log.error("", e);
                        return Result.ERROR;
                    }
                }

                return Result.OK;
            }
        });

        return submit;
    }

    public long nonBlockingCopy()
            throws IOException {
        final byte[] buf = new byte[bufSize];
        long count = 0;
        int read = 0;

        final long startTime = System.currentTimeMillis();

        while (in.available() > 0 && (read = in.read(buf)) != -1){
            if(read > 0){
                count = write(buf, count, read);
            }
        }

        if (!keepFlushing)
            out.flush();

//        final double timeSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
//        final double sizeKiB = count / 1024.0;
//        log.debug("{} KiB transferred  in {} seconds ({} KiB/s)", new Object[] { sizeKiB, timeSeconds, (sizeKiB / timeSeconds) });

        if (length != -1 && read == -1)
            throw new IOException("Encountered EOF, could not transfer " + length + " bytes");

        return count;
    }

    private long write(byte[] buf, long count, int read)
            throws IOException {
        out.write(buf, 0, read);
        count += read;
        if (keepFlushing)
            out.flush();
        listener.reportProgress(count);
        return count;
    }

}
