package bear.console;

import bear.vcs.CommandLineResult;
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
        Future<CompositeConsoleArrival.EqualityGroups> groups;

    }

    protected CompositeConsoleArrival sendCommand(final AbstractConsoleCommand command, final ConsoleCallback callback) {
        final CompositeConsoleCallContext callContext = new CompositeConsoleCallContext(consoles);

        final CountDownLatch latch = new CountDownLatch(consoles.size());

        final List<ListenableFuture<CommandLineResult>> futures = new ArrayList<ListenableFuture<CommandLineResult>>(consoles.size());

        for (int i = 0; i < consoles.size(); i++) {
            final AbstractConsole console = consoles.get(i);

            final int finalI = i;

            ListenableFuture<CommandLineResult> fut = executorService.submit(new Callable<CommandLineResult>() {
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
            });

            futures.add(fut);
        }

        return new CompositeConsoleArrival(futures, consoles, executorService);
    }

    public int size() {
        return consoles.size();
    }

    public List<? extends AbstractConsole> getConsoles() {
        return consoles;
    }
}
