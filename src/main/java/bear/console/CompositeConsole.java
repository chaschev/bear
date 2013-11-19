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

package bear.console;

import bear.vcs.CommandLineResult;
import chaschev.util.CatchyCallable;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/

/**
 * @deprecated  Not used left as an idea.
 */
@Deprecated
public class CompositeConsole {
    List<? extends AbstractConsole> consoles;

    protected ProgressMonitor progressMonitor = new ProgressMonitor();

    protected ListeningExecutorService executorService;

    public static abstract class Progress{
        public abstract void on(int console, String interval, String wholeText);
    }

    public CompositeConsole(List<? extends AbstractConsole> consoles, ProgressMonitor progressMonitor, ExecutorService executorService) {
        this.consoles = consoles;
        this.progressMonitor = progressMonitor;
        this.executorService = MoreExecutors.listeningDecorator(executorService);
    }

    public static class Result{
        Future<ConsolesDivider.EqualityGroups> groups;

    }

    protected ConsolesDivider sendCommand(final AbstractConsoleCommand command, final ConsoleCallback callback) {
        final CompositeConsoleCallContext callContext = new CompositeConsoleCallContext(consoles);

        final CountDownLatch latch = new CountDownLatch(consoles.size());

        final List<ListenableFuture<CommandLineResult>> futures = new ArrayList<ListenableFuture<CommandLineResult>>(consoles.size());

        for (int i = 0; i < consoles.size(); i++) {
            final AbstractConsole console = consoles.get(i);

            final int finalI = i;

            ListenableFuture<CommandLineResult> fut = executorService.submit(new CatchyCallable<CommandLineResult>(new Callable<CommandLineResult>() {
                @Override
                public CommandLineResult call() throws Exception {
                    CommandLineResult result = console.sendCommand(command, new ConsoleCallback() {
                        @Override
                        public void progress(AbstractConsole.Terminal terminal, String interval, String wholeText) {
                            callback.progress(terminal, interval, wholeText);
                            progressMonitor.progress(console, finalI, command, interval, wholeText);
                        }
                    });

                    callContext.consoleArrival.addArrival(finalI, result);
                    callback.whenDone(result);
                    latch.countDown();

                    return result;
                }
            }));

            futures.add(fut);
        }

        return new ConsolesDivider(null, futures, consoles, null, null);
    }

    public int size() {
        return consoles.size();
    }

    public List<? extends AbstractConsole> getConsoles() {
        return consoles;
    }
}
