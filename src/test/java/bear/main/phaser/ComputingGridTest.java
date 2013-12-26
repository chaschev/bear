package bear.main.phaser;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ComputingGridTest {
    private static final Logger logger = LoggerFactory.getLogger(ComputingGridTest.class);

    private ExecutorService service;

    @Before
    public void setUp() throws Exception {
        service = Executors.newFixedThreadPool(10);
    }

    @After
    public void tearDown() throws Exception {
        service.shutdown();
        service.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void sanityTest() throws ExecutionException, InterruptedException {
        Phase<String, Integer> phase1 = new Phase<String, Integer>(0, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            public PhaseCallable<Integer, String, Integer> apply(@Nullable Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(PhaseParty<Integer, Integer> party, int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        return "phase 1, party " + party.column;
                    }
                };
            }
        });

        ListenableFuture<List<String>> futureList =
            sampleGrid(phase1, 3)
            .startParties(service)
            .aggregateSuccessful(phase1);

        List<String> strings = futureList.get();

        assertThat(strings)
            .contains("phase 1, party " + 0)
            .contains("phase 1, party " + 1)
            .contains("phase 1, party " + 2);
    }

    @Test
    public void raiseFlagTest() throws ExecutionException, InterruptedException {
        final Phase<String, Integer> phase1 = new Phase<String, Integer>(0, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            public PhaseCallable<Integer, String, Integer> apply(@Nullable Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(PhaseParty<Integer, Integer> party, int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        return party.getName(phaseIndex);
                    }
                };
            }
        });

        Phase<String, Integer> phase2 = new Phase<String, Integer>(1, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            @Override
            public PhaseCallable<Integer, String, Integer> apply(@Nullable Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(PhaseParty<Integer, Integer> party, int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        return party.getName(phaseIndex) + ": we all depend on a cell " +
                            party.grid.cell(phase1, 2).getFuture().get(500, TimeUnit.MILLISECONDS);
                    }
                };
            }
        });

        List<String> strings = sampleGrid(phase1, phase2, 3)
            .startParties(service)
            .aggregateSuccessful(phase2)
            .get();

        System.out.println(strings);

        assertThat(strings.get(0)).contains("(1, 0): we all depend on a cell (0, 2)");
        assertThat(strings.get(1)).contains("(1, 1): we all depend on a cell (0, 2)");
        assertThat(strings.get(2)).contains("(1, 2): we all depend on a cell (0, 2)");
    }


    //exception thrown
    //aggregate results

    @Test
    public void sharedDownload_oneGuyDownloadsOtherSleepAllHaveResult() throws ExecutionException, InterruptedException {
        final AtomicInteger entryCounter = new AtomicInteger();

        final OnceEnteredCallable<String> callable = new OnceEnteredCallable<String>();

        final Phase<String, Integer> phase1 = new Phase<String, Integer>(0, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            public PhaseCallable<Integer, String, Integer> apply(Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(final PhaseParty<Integer, Integer> party, final int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        if(party.index != 2){
                            Thread.sleep(300);
                        }

                        Future<String> future = callable.runOnce(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                entryCounter.incrementAndGet();
                                Thread.sleep(300);
                                return "downloaded by " + party.getName(phaseIndex);
                            }
                        });

                        return future.get();
                    }
                };
            }
        });

        Phase<String, Integer> phase2 = new Phase<String, Integer>(1, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            public PhaseCallable<Integer, String, Integer> apply(@Nullable Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(PhaseParty<Integer, Integer> party, int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        return party.getName(phaseIndex) + ": " + party.grid.cell(phase1, 1).getFuture().get();
                    }
                };
            }
        });

        List<String> list = sampleGrid(phase1, phase2, 3)
            .startParties(service)
            .aggregateSuccessful(phase2)
            .get();

        System.out.println(list);

        assertThat(entryCounter.get()).isEqualTo(1);

        for (String s : list) {
            assertThat(s).contains("downloaded by (0, 2)");
        }
    }

    @Test
    public void sharedDownload_oneGuyDownloadsOtherFindWhoIsTheGuy() throws ExecutionException, InterruptedException {
        final AtomicInteger entryCounter = new AtomicInteger();

        final OnceEnteredCallable<String> callable = new OnceEnteredCallable<String>();

        class Phase1Result{
            String downloadResult;
            String computation;

            Phase1Result(String downloadResult, String computation) {
                this.downloadResult = downloadResult;
                this.computation = computation;
            }

            @Override
            public String toString() {
                final StringBuilder sb = new StringBuilder("Phase1Result{");
                sb.append("downloadResult='").append(downloadResult).append('\'');
                sb.append(", computation='").append(computation).append('\'');
                sb.append('}');
                return sb.toString();
            }
        }

        //guy 2 always downloads, but at phase2 they don't know who is the guy and wait for any result
        final Phase<Phase1Result, Integer> phase1 = new Phase<Phase1Result, Integer>(0, new Function<Integer, PhaseCallable<Integer, Phase1Result, Integer>>() {
            public PhaseCallable<Integer, Phase1Result, Integer> apply(Integer input) {
                return new PhaseCallable<Integer, Phase1Result, Integer>() {
                    @Override
                    public Phase1Result call(final PhaseParty<Integer, Integer> party, final int phaseIndex, Phase<Phase1Result, Integer> phase) throws Exception {
                        if(party.index != 2){
                            Thread.sleep(300);
                        }

                        Future<String> future = callable.runOnce(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                entryCounter.incrementAndGet();

                                logger.info("{}: download started", party.getName(phaseIndex));

                                Thread.sleep(600);

                                logger.info("{}: download finished", party.getName(phaseIndex));

                                return "downloaded by " + party.getName(phaseIndex);
                            }
                        });

                        if(callable.amIOwner(Thread.currentThread())){
                            return new Phase1Result(future.get(), null);
                        }else{
                            return new Phase1Result(null, party.getName(phaseIndex) + " - quick computation r");
                        }
                    }
                };
            }
        });

        final Phase<String, Integer> phase2 = new Phase<String, Integer>(1, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            public PhaseCallable<Integer, String, Integer> apply(@Nullable Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(PhaseParty<Integer, Integer> party, int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        logger.debug("{}: entered phase2", party.getName(phaseIndex));

                        List<ListenableFuture<Phase1Result>> results = party.grid.phaseFutures(phase1);

                        if(party.index != 2){
                            //when quick workers reach phase2, phase1 is not yet finish because of download task
                            assertThat(party.grid.phaseFutures(phase1).get(2).isDone()).isFalse();
                        }

                        String downloadResult = null;

                        outer: while (true) {
                            for (ListenableFuture<Phase1Result> future : results) {
                                if (future.isDone() && (downloadResult = future.get().downloadResult) != null) {
                                    break outer;
                                }
                            }

                            Thread.sleep(10);
                        }

                        if(party.index != 2){
                            // now download is finished
                            assertThat(party.grid.phaseFutures(phase1).get(2).isDone()).isTrue();
                        }

                        logger.debug("{}, got download: {}", party.getName(phaseIndex), downloadResult);

                        return party.getName(phaseIndex) + ": " + downloadResult + ", derived computation: '" + party.grid.previousResult(party, phaseIndex, Phase1Result.class).get().computation + "'";
                    }
                };
            }
        });

        ArrayList<Phase<?, Integer>> phases = Lists.<Phase<?, Integer>>newArrayList(phase1, phase2);
        ComputingGrid<Integer, Integer> grid = new ComputingGrid<Integer, Integer>(phases, intArray(3));

        List<String> list = grid
            .startParties(service)
            .aggregateSuccessful(phase2)
            .get();


        assertThat(entryCounter.get()).isEqualTo(1);

        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            System.out.println(s);
            assertThat(s).contains("downloaded by (0, 2)");

            if(i == 2){
                assertThat(s).contains("computation: 'null'");
            }else{
                assertThat(s).contains("derived computation: '(0, " + i +")");
            }
        }
    }

    private static List intArray(int partiesCount) {
        List r = new ArrayList();
        for(int i = 0;i<partiesCount;i++){
            r.add(i);
        }
        return r;
    }

    private static ComputingGrid<Integer, Integer> sampleGrid(List<? extends Phase<?, Integer>> phases, int partiesCount) {
        return new ComputingGrid<Integer, Integer>(phases, intArray(partiesCount));
    }

    @Test
    public void exceptionsTest() throws ExecutionException, InterruptedException {

        List<String> strings;

        strings = exceptionExperiment(true, 0);

        assertThat(strings.get(0)).isNull();
        assertThat(strings.get(1)).isNotNull();
        assertThat(strings.get(2)).isNotNull();

        strings = exceptionExperiment(true, 2);

        assertThat(strings.get(0)).isNull();
        assertThat(strings.get(1)).isNull();
        assertThat(strings.get(2)).isNull();

        strings = exceptionExperiment(false, 0);

        assertThat(strings.get(0)).isNull();
        assertThat(strings.get(1)).isNotNull();
        assertThat(strings.get(2)).isNotNull();

        strings = exceptionExperiment(false, 2);

        assertThat(strings.get(0)).isNotNull();
        assertThat(strings.get(1)).isNotNull();
        assertThat(strings.get(2)).isNull();

    }

    private List<String> exceptionExperiment(final boolean allDepend, final int whoThrows) throws InterruptedException, ExecutionException {
        final Phase<String, Integer> phase1 = new Phase<String, Integer>(0, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            public PhaseCallable<Integer, String, Integer> apply(@Nullable Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(PhaseParty<Integer, Integer> party, int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        if(whoThrows == party.index){
                            throw new RuntimeException(party.index + "");
                        }

                        return party.getName(phaseIndex);
                    }
                };
            }
        });
        Phase<String, Integer> phase2 = new Phase<String, Integer>(1, new Function<Integer, PhaseCallable<Integer, String, Integer>>() {
            @Override
            public PhaseCallable<Integer, String, Integer> apply(@Nullable Integer input) {
                return new PhaseCallable<Integer, String, Integer>() {
                    @Override
                    public String call(PhaseParty<Integer, Integer> party, int phaseIndex, Phase<String, Integer> phase) throws Exception {
                        if(allDepend){
                            return party.getName(phaseIndex) + ": we all depend on a cell " +
                                party.grid.cell(phase1, 2).getFuture().get(500, TimeUnit.MILLISECONDS);
                        }else{
                            return party.grid.cell(phase1, party.index).getFuture().get() + ", "  + party.getName(phaseIndex);
                        }
                    }
                };
            }
        });

        ComputingGrid<Integer, Integer> grid =
            sampleGrid(phase1, phase2, 3)
            .startParties(service);

        List<String> list = grid
            .aggregateSuccessful(phase2)
            .get();

        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if(s == null){
                assertThat(grid.parties.get(i).exception).isNotNull();
                assertThat(grid.parties.get(i).state).isEqualTo(PartyState.BROKEN);
            }
        }

        System.out.println(list);

        return list;
    }

    private static ComputingGrid<Integer, Integer> sampleGrid(Phase<String, Integer> phase1,  int partiesCount) {
        return sampleGrid(phase1, null, partiesCount);
    }

    private static ComputingGrid<Integer, Integer> sampleGrid(Phase<String, Integer> phase1, Phase<String, Integer> phase2, int partiesCount) {
        ArrayList<Phase<?, Integer>> phases = Lists.<Phase<?, Integer>>newArrayList(phase1);

        if (phase2 != null){
            phases.add(phase2);
        }

        return sampleGrid(phases, partiesCount);
    }
}
