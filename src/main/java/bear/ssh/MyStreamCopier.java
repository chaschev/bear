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

package bear.ssh;

import bear.core.GlobalContext;
import bear.task.TaskResult;
import chaschev.lang.OpenBean;
import chaschev.util.CatchyCallable;
import chaschev.util.Exceptions;
import com.google.common.base.Throwables;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class MyStreamCopier {

    private long periodMs;
    private int periodNano;
    //this field is wrong, but left is something breaks
    private volatile boolean finished;
//    private long finishAtMs = -1;

    public boolean isStdErr;
    private long count;
    private volatile long finishAtMs;
    private Field eofField;

    public void triggerCopy() {
        try {
            nonBlockingCopy();
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

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

    public MyStreamCopier(InputStream in, OutputStream out, boolean stdErr) {
        this.in = in;
        this.out = out;
        isStdErr = stdErr;
        eofField = OpenBean.getField(in, "eof").get();
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

    public Future<TaskResult> spawn(ExecutorService service, final long finishAtMs) {
        this.finishAtMs = finishAtMs;
        return service.submit(new CatchyCallable<TaskResult>(new Callable<TaskResult>() {
            @Override
            public TaskResult call() {
                boolean interrupted = false;

                while (!stopFlag) {
                    interrupted = Thread.currentThread().isInterrupted();

                    if(interrupted) break;

                    try {
                        if(nonBlockingCopy() == -1) {
                            break;
                        }

                        if(MyStreamCopier.this.finishAtMs != -1 && MyStreamCopier.this.finishAtMs < System.currentTimeMillis()){
                            break;
                        }

                        listener.reportProgress(count, null, -1);

                        if(!(periodMs <= 0 && periodNano <=0)){
                            Thread.sleep(periodMs, periodNano);
                        }
                    }
                    catch (Exception e) {
                        if(e instanceof InterruptedIOException){
                            GlobalContext.AwareThread t = (GlobalContext.AwareThread) Thread.currentThread();
                            log.error("interrupted by: {}, at: {}, I am at {}",
                                t.getInterruptedBy(),
                                Throwables.getStackTraceAsString(t.getInterruptedAt()),
                                Throwables.getStackTraceAsString(e)
                            );
                        }else{
                            log.error("", e);
                        }
                        return new TaskResult(e);
                    }
                }

                try {
                    nonBlockingCopy();


                    // try one more time as it's buggy
                    // they asked us to stop but did not interrupt, let's have one more chance
                    //todo remove this
/*
                    if(stopFlag){
                        try {
                            Thread.sleep(periodMs, periodNano);
                        } catch (InterruptedException e) {
                            //they are interrupting our attempt to wait
                            //we cancel and try to instantly copy...
                            nonBlockingCopy();
                        }
                    }
*/
                } catch (Exception e) {
                    log.error("", e);

                    return new TaskResult(e);
                } finally {
                    if(stopFlag || interrupted){
                        IOUtils.closeQuietly(in);
                    }
                }

                finished = true;

                return TaskResult.OK;
            }
        }));
    }

    public long nonBlockingCopy() throws Exception {
        final byte[] buf = new byte[bufSize];
        count = 0;
        int read;
        int avaliable;

        //(avaliable = in.available()) > 0
        while ((read = in.read(buf)) != -1){
//            LoggerFactory.getLogger("log").trace("nonBlockingCopy: {}", read);
            if(read > 0){
                count = write(buf, count, read);
            }else{
                break;
            }
        }

        LoggerFactory.getLogger("log").trace("nonBlockingCopy: {}", read);

        if (!keepFlushing){
            out.flush();
        }

//        if(count == 0 && (Boolean)eofField.get(in)){
//            System.out.println("let's inspect buffer....:" + ((ByteArrayOutputStream)out).toString());
//        }

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

    public void setFinishAtMs(long finishAtMs) {
        this.finishAtMs = finishAtMs;
    }
}
