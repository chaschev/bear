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

package bear.plugins.misc;

import bear.context.HavingContext;
import bear.core.Bear;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.vcs.BranchInfo;
import bear.vcs.VCSSession;
import bear.vcs.VcsLogInfo;
import chaschev.json.JacksonMapper;
import chaschev.util.Exceptions;
import com.bethecoder.table.ASCIITableHeader;
import com.bethecoder.table.AsciiTableInstance;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

import static bear.task.TaskResult.okOrAbsent;
import static com.bethecoder.table.ASCIITableHeader.h;
import static com.google.common.base.Optional.*;

/**
 * Releases are saved as JSON cache in root releases folders.
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Releases extends HavingContext<Releases, SessionContext>{
    private static final Logger logger = LoggerFactory.getLogger(Releases.class);

    static final JacksonMapper JACKSON_MAPPER = new JacksonMapper().prettyPrint(true);

    ReleasesPlugin releases;

    final DynamicVariable<Integer> retrieveLastXCommits = Variables.newVar(5);

    Bear bear;
    String current;

    transient TreeSet<String> folders;

    /**
     * Release is null in cache when there is no such release on the machine.
     */
    protected final Map<String, Release> folderMap = new TreeMap<String, Release>();
    private boolean loaded;
    private final ObjectMapper mapper;

    public Releases(SessionContext $, ReleasesPlugin releases) {
        super($);
        this.releases = releases;
        folders = new TreeSet<String>();
        mapper = new ObjectMapper();
    }

    public Release activatePending(PendingRelease pendingRelease) {
        logger.info("activating release {}", pendingRelease);

        checkLoaded();

        String releasePath = $(releases.releasePath);

        $.sys.move(pendingRelease.path).to(releasePath).run();

        removeItem(pendingRelease.path);

        Release activeRelease = new Release(pendingRelease.log, pendingRelease.branchInfo, releasePath, "active");

        folders.add(releasePath);
        folderMap.put(releasePath, activeRelease);

        makeActive(activeRelease);

        sort();

        switchLinkTo(releasePath);

        cleanupAndSave();


//        saveJson();

        return activeRelease;
    }

    private void makeActive(Release activeRelease) {
        for (Release release : folderMap.values()) {
            if(release == null || "failed".equals(release.status)) continue;
            release.status = "inactive";
        }

        activeRelease.status = "active";

        $.putConst(releases.activatedRelease, Optional.of(activeRelease));
    }


    public Optional<Release> getCurrentRelease() {
        checkLoaded();

        if(current == null){
            return Optional.absent();
        }

        Optional<Release> currentRelease = findByRef(ReleaseRef.path(current));

        if(currentRelease.isPresent()){
            makeActive(currentRelease.get());
        }

        return currentRelease;
    }

    public Optional<Release> getRelease(String folder){
        checkLoaded();

        Optional<Release> optional = computeRelease(folder);

        if(!folderMap.containsKey(folder)){
            folderMap.put(folder, optional.orNull());
        }

        if(!folders.contains(folder)){
            folders.add(folder);
        }

        return fromNullable(folderMap.get(folder));
    }

    public Releases load(){
        loadCache();
        current = $.sys.readLink($(releases.currentReleaseLinkPath));
        loaded = true;

//        if(logger.isDebugEnabled()){
//            logger.debug("loaded releases:\n{}", show());
//        }

        return this;
    }

    public String last() {
        checkLoaded();
        return folders.last();
    }

    public String previous() {
        Iterator<String> it = folders.descendingIterator();
        it.next();
        return it.next();
    }



    public PendingRelease newPendingRelease(){
        checkLoaded();

        return newPendingRelease(Optional.<BranchInfo>absent(), Optional.<VcsLogInfo>absent());
    }

    public PendingRelease newPendingRelease(
        @Nonnull Optional<BranchInfo> branchInfo, @Nonnull Optional<VcsLogInfo> vcsLogInfo){
        checkLoaded();

        String pendingPath = $(releases.pendingReleasePath);

        $.sys.mkdirs(pendingPath).run();

        PendingRelease release;

        if(!branchInfo.isPresent() || !vcsLogInfo.isPresent()){
            Optional<Release> temp = computeRelease(pendingPath);

            if(!temp.isPresent()){
                throw new RuntimeException("error occurred while computing release: " + pendingPath + ". it might not exist");
            }

            release = new PendingRelease(temp.get().log, temp.get().branchInfo, temp.get().path, this);
        }else{
            release = new PendingRelease(vcsLogInfo, branchInfo, pendingPath, this);
        }

        folders.add(pendingPath);
        folderMap.put(pendingPath, release);

        sort();

        saveJson();

        $.putConst(releases.pendingRelease, release);
        $.putConst(releases.rollbackToRelease, getCurrentRelease());

        logger.info("newPendingRelease - RELEASES:\n{}", show());

        return release;
    }

    public void rollbackTo(Release release){
        rollbackTo(ReleaseRef.path(release.path));
    }

    public void rollbackTo(ReleaseRef releaseRef){
        checkLoaded();

        Optional<Release> release = findByRef(releaseRef);

        checkPresent(releaseRef, release);

        switchLinkTo(release.get().path);
    }

    public void deleteRelease(ReleaseRef releaseRef){
        checkLoaded();

        Optional<Release> release = findByRef(releaseRef);

        checkPresent(releaseRef, release);

        String path = release.get().path;

        if(path.equals(current)){
            throw new IllegalArgumentException("won't delete current release: " + releaseRef);
        }

        removeItem(path);

        $.sys.rm(path).run();

        saveJson();
    }

    public void cleanupAndSave(){
        checkLoaded();

        int keepX = $(releases.keepXReleases);

        if($(releases.cleanPending)){
            String path = $(releases.path) + "/" + $(releases.pendingName) + "*";
            $.sys.rm(path).sudo().run();
        }

        if(keepX > 0){
            delete(listToDelete(keepX));
        }

        saveJson();
    }

    void delete(List<String> toDelete) {
        checkLoaded();

        Preconditions.checkArgument(!Iterables.contains(toDelete, current), "won't delete current folder!");

        for (String s : toDelete) {
            removeItem(s);
        }

        //apps may be run under a root and create files with root's owner
        String[] paths = toDelete.toArray(new String[toDelete.size()]);

        $.sys.rm(paths).sudo().run();

        sort();
    }

    public void invalidateCache(){
//        checkLoaded();

        folderMap.clear();
    }

    private void checkLoaded() {
        Preconditions.checkArgument(loaded, "you need to call load() to load the data");
    }

    private static void checkPresent(ReleaseRef releaseRef, Optional<Release> release) {
        if(!release.isPresent()){
            throw new IllegalArgumentException("no such release: " + releaseRef);
        }
    }

    private void switchLinkTo(String path) {
        current = path;

        $.sys.link($(releases.currentReleaseLinkPath)).toSource(path).run();

        getCurrentRelease(); //makes it active
    }

    public Optional<Release> findAny(String labelOrPath) {
        return findByRef(ReleaseRef.label(labelOrPath)).or(findByRef(ReleaseRef.path(labelOrPath)));
    }

    public Optional<Release> findByRef(ReleaseRef releaseRef) {
        String path ;

        if(releaseRef.isLabel()){
            path = $(releases.path)  + "/" + releaseRef.label;
        }else{
            path = releaseRef.path;
        }

        return getRelease(path);
    }

    private void removeItem(String s) {
        folderMap.remove(s);
        folders.remove(s);
    }

    void saveJson() {
        try {
            $.sys.writeString(JACKSON_MAPPER.toJSON(folderMap)).toPath($(releases.releasesJsonPath)).run();
        } catch (Exception e) {
            logger.warn("unable to save JSON", e);
        }
    }

    protected Releases loadCache(){
        folders.addAll(ls());

        try{
            Map<String, Release> map = loadMap();

            Iterator<String> it;

            it = folders.iterator();

            while (it.hasNext()) {
                String next = it.next();
                if(next.endsWith("/current")) {it.remove(); break;}
            }

            folderMap.putAll(map);

            for (it = folderMap.keySet().iterator(); it.hasNext(); ) {
                String s = it.next();
                if (!folders.contains(s)) {
                    logger.debug("removing missing item from cache: {}", s);
                    it.remove();
                }
            }

            sort();

            return this;
        }catch (Exception e){
            logger.warn("error during loading the cache", e);
            invalidateCache();
            return this;
        }
    }

    protected Map<String, Release> loadMap() {
        try {
            String json = $.sys.readString($(releases.releasesJsonPath), null);
            return mapper.readValue(json, new TypeReference<Map<String, Release>>() {
            });
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
//        return JACKSON_MAPPER.fromJSON(, Map.class);
    }


    List<String> ls() {
        return $.sys.ls($(releases.path)).absolutePaths().run().getLines();
    }

    Optional<Release> computeRelease(String folder) {
        if(!$.sys.exists(folder)){
            return absent();
        }

        VCSSession vcs = $(bear.vcs);

        VcsLogInfo vcsLogInfo = vcs.logLastN($(retrieveLastXCommits)).run();

//        if(!vcsLogInfo.ok()){
//            return absent();
//        }

        BranchInfo branchInfo = vcs.queryRevision($(bear.revision)).run();

//        if(!branchInfo.ok()){
//            return absent();
//        }

        return of(new Release(okOrAbsent(vcsLogInfo), okOrAbsent(branchInfo), folder, null));
    }

    List<String> listToDelete(int keepX) {
        if (folders.size() <= keepX) return Collections.emptyList();

        int toIndex = folders.size() - keepX;

        List<String> folders = new ArrayList<String>(this.folders);

        List<String> toDelete = new ArrayList<String>(folders.subList(0, toIndex));

        if(toDelete.contains(current)){
            toDelete.remove(current);
            if(toIndex < folders.size()){
                toDelete.add(folders.get(toIndex));
            }
        }

        return toDelete;
    }

    private void sort() {

    }

    public String show(){
        getCurrentRelease();

        Collection<Release> values = folderMap.values();

        List<String[]> table = new ArrayList<String[]>(values.size());

        for (Release release : values) {
            if(release == null) continue;

            table.add(new String[]{
                release.name(),
                    release.getLastAuthor(),
                    release.branchInfo.isPresent() ? release.branchInfo.get().revision : "",
                    release.log.isPresent() ? release.log.get().firstComment().replace("\n", " ") : "",
                    release.status
            });
        }

        return AsciiTableInstance.get().getTable(
                new ASCIITableHeader[]{h("Name"), h("Author"), h("Revision").maxWidth(10), h("Comment").maxWidth(50), h("Status")},
                table.toArray(new String[table.size()][])
            );
    }

    public void markRollback(Release release) {
        mark(release, "rollback");
    }

    public void markFailed(Release release) {
        mark(release, "failed");
    }

    private void mark(Release release, String status) {
        release.status = status;
        saveJson();
    }
}
