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

package bear.main;

import bear.annotations.Project;
import bear.context.CompilationResult;
import bear.core.BearProject;
import chaschev.lang.LangUtils;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompileManager {
    private static final Logger logger = LoggerFactory.getLogger(CompileManager.class);

    protected final File buildDir;
    protected final Compiler groovyCompiler;
    private final List<File> sourceDirs;

    @Nullable
    protected CompilationResult lastCompilationResult;
    protected Optional<ClassLoader> dependenciesCL = Optional.absent();

    public CompileManager(List<File> sourceDirs, File buildDir) {
        this.sourceDirs = sourceDirs;
        this.buildDir = buildDir;
        this.groovyCompiler = new GroovyCompiler(sourceDirs, buildDir);
    }

    public synchronized CompilationResult compileWithAll() {
        logger.info("compiling...");

        Stopwatch sw = Stopwatch.createStarted();

        if(lastCompilationResult != null && System.currentTimeMillis() - lastCompilationResult.timestamp() < 300){
            logger.debug("cancelled compilation, up to date");
        }else{
            lastCompilationResult = groovyCompiler.compile(dependenciesCL.or(getClass().getClassLoader()));
        }

        logger.info("compilation finished in {}s", LangUtils.millisToSec(sw.elapsed(TimeUnit.MILLISECONDS)));

        return lastCompilationResult;
    }

    @Nonnull
    public Optional<CompiledEntry<?>> findClass(final String className) {
        compileWithAll();
        return (Optional)lastCompilationResult.byName(className);
    }

    public File findScript(final String name) {
//        return new File(sourcesDir, name);
        throw new UnsupportedOperationException("todo remove me?");
    }

    @Nonnull
    public Optional<CompiledEntry<?>> findClass(File file) {
        return (Optional)lastCompilationResult.byFile(file);
    }

    public Optional<CompiledEntry<? extends BearProject>> findProjectShort(String shortName){
        compileWithAll();

        for (CompiledEntry<?> entry : lastCompilationResult.getEntries()) {
            Project project = entry.aClass.getAnnotation(Project.class);

            if(project == null) continue;

            if(project.shortName().equals(shortName)) return (Optional)Optional.of(entry);
        }

        return Optional.absent();
    }

    public Optional<CompiledEntry<? extends BearProject>> findProject(String name){
        return (Optional)findProjectShort(name).or((Optional)findClass(name));
    }

    public List<File> getSourceDirs() {
        return sourceDirs;
    }

    public List<CompiledEntry<? extends BearProject>> findProjects() {
        compileWithAll();

        List<CompiledEntry<? extends BearProject>> results = new ArrayList<CompiledEntry<? extends BearProject>>();

        for (CompiledEntry<?> entry : lastCompilationResult.getEntries()) {
            Project project = entry.aClass.getAnnotation(Project.class);

            if(project == null) continue;

            results.add((CompiledEntry) entry);
        }

        return results;
    }

    public void setDependenciesCL(Optional<ClassLoader> dependenciesCL) {
        this.dependenciesCL = dependenciesCL;
    }
}
