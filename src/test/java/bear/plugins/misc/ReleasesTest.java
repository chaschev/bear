package bear.plugins.misc;

import bear.context.DependencyInjection;
import bear.plugins.sh.SessionTest;
import bear.session.Result;
import bear.vcs.BranchInfo;
import bear.vcs.GitCLIPluginTest;
import chaschev.lang.Functions2;
import chaschev.lang.Lists2;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;

import static bear.plugins.misc.ReleaseRef.label;
import static com.google.common.base.Optional.of;
import static com.google.common.base.Predicates.*;
import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

//too much to fix in this test, using integration tests to test it
@Ignore
public class ReleasesTest extends SessionTest{

    private final ReleasesPlugin plugin;
    private final Releases releases;

    public ReleasesTest() {
        plugin = new ReleasesPlugin(g);
        DependencyInjection.nameVars(plugin, g);

        doReturn(null).when(sys).readString(anyString(), anyString());

        releases = sampleReleases();

        stubSendCommand();
    }

    @Test
    public void testCreation() throws Exception {
        Optional<Release> path1 = releases.getRelease("path1");

        assertThat(path1.isPresent()).isTrue();
        assertThat(path1.get().path).isEqualTo("path1");
    }

    private Releases sampleReleases() {
        doReturn(newArrayList(l("path1"), l("path2"), l("path3"))).when(sys).ls(releasesPath());
        doReturn(null).when(sys).readLink(l("current"));

        Releases releases = Mockito.spy($(plugin.session));
        Map<String, Release> map = new HashMap<String, Release>();

        Release path1Release = addReleaseToCache(map, "path1");

        doReturn(map).when(releases).loadMap();

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock inv) throws Throwable {
                String path = (String) inv.getArguments()[0];

                if(path.contains("does not exist")){
                    return null;
                }

                return newRelease(path);
            }
        }).when(releases).computeRelease(anyString());

//        doReturn(newRelease("temp")).when(releases).computeRelease(anyString());

        doReturn("path10").when($.sys).readLink("/var/lib/unitTests/current");

        releases.load();
        return releases;
    }

    private Release addReleaseToCache(Map<String, Release> map, String label) {
        Release release = newRelease(l(label));
        map.put(l(label), release);
        return release;
    }

    private Release newRelease(String path) {
        return new Release(of(GitCLIPluginTest.vcsLogSample()),
            of(new BranchInfo("auth1", "r1", "d1")), path, "ok");
    }

    @Test
    public void newPendingRelease() throws Exception {
        TreeMap<String, Release> mapBefore = new TreeMap<String, Release>(releases.folderMap);
        TreeSet<String> foldersBefore = new TreeSet<String>(releases.folders);

        Release release = releases.newPendingRelease();

        // THEN:
        // creates a folder
        // add release entry
        // saves JSON

        verify(sys, times(1)).mkdirs(release.path).run();
        verify(releases, times(1)).saveJson();

        assertThat(releases.folderMap).containsKey(release.path);
        assertThat(releases.folders).contains(release.path);

        assertThat(mapBefore).doesNotContainKey(release.path);
        assertThat(foldersBefore).doesNotContain(release.path);
    }

    @Test
    public void testRollbackTo() throws Exception {
        // updates the current link if folder exists

        System.out.println(releases.show());

        try {
            releases.rollbackTo(label("does not exist 1"));
            fail();
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("no such release");
        }

        releases.rollbackTo(label("path2"));

        assertThat(releases.current).isEqualTo(l("path2"));
    }

    private static String l(String label) {
        return releasesPath() + "/" + label;
    }

    private static String releasesPath() {
        return "/var/lib/unitTests/releases";
    }

    @Test
    public void testDeleteRelease() throws Exception {

        // checks the release is not current
        // deletes folder, removes entry
        // saves JSON


        releases.current = l("path1");

        try {
            releases.deleteRelease(label("path1"));
            fail();
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("won't delete current release");
        }

        //normal delete
        addReleaseToCache(releases.folderMap, "path2");

        assertThat(releases.folderMap).containsKey(l("path2"));
        assertThat(releases.folders).contains(l("path2"));

        releases.deleteRelease(label("path2"));

        verify(sys, times(1)).rm(l("path2")).run();

        assertThat(releases.folders).doesNotContain(l("path2"));
        assertThat(releases.folderMap).doesNotContainKey(l("path2"));
        verify(releases, times(1)).saveJson();


        //delete non-cached

        assertThat(releases.folderMap).doesNotContainKey(l("path3"));
        assertThat(releases.folders).contains(l("path3"));

        releases.deleteRelease(label("path3"));

        verify(sys, times(1)).rm(l("path3")).run();
        verify(releases, times(2)).saveJson();

        assertThat(releases.folders).doesNotContain(l("path3"));
        assertThat(releases.folderMap).doesNotContainKey(l("path3"));
    }

    private static void fail() {
        throw new AssertionError("exception must be thrown");
    }

    @Test
    public void testListToDelete() throws Exception {
        releases.current = l("path3");

        assertThat(releases.listToDelete(2)).containsExactly(l("path1"));
        assertThat(releases.listToDelete(1)).containsExactly(l("path1"), l("path2"));
        assertThat(releases.listToDelete(0)).containsExactly(l("path1"), l("path2"));

        releases.current = "none";

        assertThat(releases.listToDelete(0)).containsExactly(l("path1"), l("path2"), l("path3"));

        //skips current
        releases.current = l("path2");

        assertThat(releases.listToDelete(1)).containsExactly(l("path1"), l("path3"));
    }

    @Test
    public void testCleanup() throws Exception {

        $.putConst(plugin.keepXReleases, 1);

        addReleaseToCache(releases.folderMap, "path2");
        addReleaseToCache(releases.folderMap, "path3");

        releases.current = l("path3");

        releases.cleanupAndSave();

        assertThat(releases.folderMap.keySet()).containsExactly(l("path3"));
        assertThat(releases.folders).containsExactly(l("path3"));

//        verify(sys, times(1)).rm(l("path1"), l("path2"));
        verify(releases, times(1)).saveJson();
    }

    @Test
    public void testActivatePending() throws Exception {
        doReturn(Result.OK).when(sys).link(anyString()).run();
        doReturn(Result.OK).when(sys).rm(anyString()).run();

        PendingRelease pendingRelease = releases.newPendingRelease();

        assertThat(pendingRelease.status).isEqualTo("pending");
        assertThat(releases.getRelease(pendingRelease.path)).isNotNull();

        Release activeRelease = pendingRelease.activate();

        assertThat(activeRelease.status).isEqualTo("active");

        assertThat(
            new HashSet<String>(Lists2.projectField(newArrayList(Iterables.filter(releases.folderMap.values(), not(equalTo(activeRelease)))),
                Release.class, String.class, "status"))
        ).containsExactly("inactive");

        assertThat(Iterables.tryFind(Iterables.transform(
            releases.folderMap.values(), Functions2.<Release, String>field("path")), containsPattern("pending")).isPresent()).isFalse();

        assertThat(releases.current).isEqualTo(activeRelease.path);
        assertThat(pendingRelease.path).isNotEqualTo(activeRelease.path);

        assertThat(releases.folders).doesNotContain(pendingRelease.path);
        assertThat(releases.folderMap).doesNotContainKey(pendingRelease.path);

        assertThat(releases.folders).contains(activeRelease.path);
        assertThat(releases.folderMap).containsKey(activeRelease.path);

        verify(sys, atLeast(1)).link(activeRelease.path).toSource($(plugin.currentReleaseLinkPath)).run();
        verify(sys, atLeast(1)).rm($(plugin.currentReleaseLinkPath)).run();


    }

    @Test
    public void testJsonSerialization() throws Exception {
        String s = Releases.JACKSON_MAPPER.toJSON(releases.folderMap);


        ObjectMapper mapper = new ObjectMapper();
        Map<String, Release> o = mapper.readValue(s, new TypeReference<Map<String, Release>>() {
        });

        assertThat(o.values().iterator().next().log.get().entries.size()).isGreaterThan(1);
    }

    @After
    public void tearDown() throws Exception {
        System.out.println(releases.show());
    }
}
