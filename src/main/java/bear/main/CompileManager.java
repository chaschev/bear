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

import bear.context.CompilationResult;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompileManager {
    private static final Logger logger = LoggerFactory.getLogger(CompileManager.class);

    protected final File sourcesDir;
    protected final File buildDir;
    protected final Compiler javaCompiler;

    @Nullable
    CompilationResult lastCompilationResult;

    public CompileManager(File sourcesDir, File buildDir) {
        this.sourcesDir = sourcesDir;
        this.buildDir = buildDir;
        this.javaCompiler = new GroovyCompiler(sourcesDir, buildDir);
    }

    public synchronized CompilationResult compileWithAll() {
        logger.info("compiling...");

        //this is a hack. todo: change to 300ms, add compiler's up-to-date check
        if(lastCompilationResult != null && System.currentTimeMillis() - lastCompilationResult.timestamp() < 300){
            logger.info("cancelled compilation, up to date");
        }

        CompilationResult result = lastCompilationResult = javaCompiler.compile();

        logger.info("compilation finished");

        return result;
    }

    @Nonnull
    public Optional<CompiledEntry> findClass(final String className) {
        compileWithAll();
        return lastCompilationResult.byName(className);
    }

    public File findScript(final String name) {
        return new File(sourcesDir, name);
    }

    @Nonnull
    public Optional<CompiledEntry> findClass(File file) {
        return lastCompilationResult.byFile(file);
    }
}
