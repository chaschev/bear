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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompileManager {
    private static final Logger logger = LoggerFactory.getLogger(CompileManager.class);

    protected final File sourcesDir;
    protected final File buildDir;
    protected final JavaCompiler2 javaCompiler;

    @Nullable
    FXConf.CompilationResult lastCompilationResult;

    public CompileManager(File sourcesDir, File buildDir) {
        this.sourcesDir = sourcesDir;
        this.buildDir = buildDir;
        this.javaCompiler = new JavaCompiler2(sourcesDir, buildDir);
    }

    public synchronized FXConf.CompilationResult compileWithAll() {
        logger.info("compiling...");

        //this is a hack. todo: change to 300ms, add compiler's up-to-date check
        if(lastCompilationResult != null && System.currentTimeMillis() - lastCompilationResult.timestamp < 3000){
            logger.info("cancelled, up to date");
        }

        return lastCompilationResult = javaCompiler.compile();
    }

    public CompiledEntry findClass(final String className) {
        CompiledEntry aClass = findClass(className, true);

        if (aClass == null) {
            aClass = findClass(className, false);

            if (aClass == null) {
                throw new RuntimeException("class not found: " + className);
            }

            return aClass;
        } else {
            return aClass;
        }
    }

    public File findScript(final String name) {
        return new File(sourcesDir, name);
    }

    public CompiledEntry findClass(final String filePath, boolean script) {
//        if(lastCompilationResult == null){
//            logger.info("not compiled yet, compiling...");
            compileWithAll();
//        }

        final String className = FilenameUtils.getBaseName(filePath);

        Preconditions.checkNotNull(lastCompilationResult, "you need to compile first to load classes");

        return Iterables.find(script ?
            lastCompilationResult.scriptClasses : lastCompilationResult.settingsClasses, new Predicate<CompiledEntry>() {
            @Override
            public boolean apply(CompiledEntry input) {
                return input.aClass.getSimpleName().equals(className);
            }
        }, null);
    }
}
