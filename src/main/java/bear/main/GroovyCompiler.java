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
import chaschev.util.Exceptions;
import com.google.common.base.Optional;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Optional.of;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class GroovyCompiler extends Compiler {
    private static final Logger logger = LoggerFactory.getLogger(GroovyCompiler.class);
    private GroovyClassLoader gcl;
    private final Map<File,GroovyCodeSource> sourceMap = new HashMap<File, GroovyCodeSource>();
    private final Map<String,CompiledEntry> simpleNameMap = new HashMap<String, CompiledEntry>();
    private final Map<String,CompiledEntry> nameMap = new HashMap<String, CompiledEntry>();

    GroovyCompiler(List<File> sourcesDir, File buildDir) {
        super(sourcesDir, buildDir);
    }

    @Override
    public String[] extensions() {
        return new String[]{"groovy"};
    }

    public CompilationResult compile(ClassLoader parentCL) {
        compileScripts(sourceDirs, parentCL);

        final long now = System.currentTimeMillis();

        return new CompilationResult() {
            @Override
            public Optional<? extends CompiledEntry> byName(String name) {
                CompiledEntry<?> entry = simpleNameMap.get(name);

                if(entry != null){
                    return of(entry);
                }

                return fromNullable(nameMap.get(name));
            }

            @Override
            public Optional<? extends CompiledEntry> byFile(File file) {
                try {
                    for (CompiledEntry e : nameMap.values()) {
                        if(e.file.getCanonicalPath().equals(file.getCanonicalPath())){
                            return of(e);
                        }
                    }

                    return absent();
                } catch (IOException e) {
                    throw Exceptions.runtime(e);
                }
            }

            @Override
            public long timestamp() {
                return now;
            }

            @Override
            public Collection<? extends CompiledEntry> getEntries() {
                return nameMap.values();
            }
        };
    }

    public synchronized GroovyClassLoader compileScripts(List<File> sourceDirs, ClassLoader parentCL) {
        nameMap.clear();
        simpleNameMap.clear();

        if(gcl == null){
            gcl = new GroovyClassLoader(parentCL);
        }

        gcl.addClasspath(buildDir.getAbsolutePath());

        for (File sourceDir : sourceDirs) {
            List<File> groovySources = new ArrayList<File>(FileUtils.listFiles(sourceDir, extensions, true));

            try {
                for (File file : groovySources) {
                    GroovyCodeSource source = sourceMap.get(file);

                    if(source == null){
                        sourceMap.put(file, source = new GroovyCodeSource(file, "UTF-8"));
                    }

                    logger.info("compiling {}...", file);

                    Class aClass = gcl.parseClass(source);

                    CompiledEntry<?> e = new CompiledEntry(aClass, file, "groovy");

                    simpleNameMap.put(aClass.getSimpleName(), e);
                    nameMap.put(aClass.getName(), e);
                }
            } catch (IOException e) {
                throw Exceptions.runtime(e);
            }
        }

        return gcl;
    }
}
