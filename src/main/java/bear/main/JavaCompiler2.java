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

import bear.context.JavaCompilationResult;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileFilter;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class JavaCompiler2 extends Compiler {
    private static final Logger logger = LoggerFactory.getLogger(JavaCompiler2.class);
    private URLClassLoader classLoader;

    JavaCompiler2(List<File> sourceDirs, File buildDir) {
        super(sourceDirs, buildDir);
    }


    @Override
    public String[] extensions() {
        return new String[]{"java"};
    }

    public JavaCompilationResult compile(ClassLoader parentCL) {
        throw new UnsupportedOperationException("remove Java");
//        final List<File> filesList = compileScripts(sourcesDir);
//
//        try {
//            classLoader = new URLClassLoader(new URL[]{buildDir.toURI().toURL()}, getClass().getClassLoader());
//        } catch (MalformedURLException e) {
//            throw Exceptions.runtime(e);
//        }
//
//        JavaCompilationResult result = new JavaCompilationResult();
//
//        for (File file : filesList) {
//            try {
//                Class aClass = (Class) classLoader.loadClass(getBaseName(file.getName()));
//
//                if (Script.class.isAssignableFrom(aClass)) {
//                    result.scriptClasses.add(new CompiledEntry(aClass, file, "java"));
//                } else if (BearProject.class.isAssignableFrom(aClass)) {
//                    result.settingsClasses.add(new CompiledEntry(aClass, file, "java"));
//                }
//
//            } catch (ClassNotFoundException e) {
//                throw Exceptions.runtime(e);
//            }
//        }
//
//        result.timestamp = System.currentTimeMillis();
//
//        return result;
    }

    public List<File> compileScripts(File sourcesDir) {
        FileFilter filter = new SuffixFileFilter(extensions);

        final File[] files = sourcesDir.listFiles(filter);

        final ArrayList<String> params = newArrayListWithExpectedSize(files.length);

        if (!buildDir.exists()) {
            buildDir.mkdir();
        }

        Collections.addAll(params, "-d", buildDir.getAbsolutePath());
        List<File> javaFilesList = newArrayList(files);

        List<File> filesListToCompile = ImmutableList.copyOf(Iterables.filter(javaFilesList, new Predicate<File>() {
            @Override
            public boolean apply(File javaFile) {
                File classFile = new File(buildDir, FilenameUtils.getBaseName(javaFile.getName()) + ".class");

                boolean upToDate = classFile.exists() && classFile.lastModified() > javaFile.lastModified();

                if(upToDate){
                    logger.info("{} is up-to-date", javaFile);
                }

                return !upToDate;
            }
        }));

        if(filesListToCompile.isEmpty()){
            logger.info("all files are up-to-date");
            return javaFilesList;
        }

        final List<String> filePaths = Lists.transform(filesListToCompile, new Function<File, String>() {
            public String apply(File input) {
                return input.getAbsolutePath();
            }
        });

        params.addAll(filePaths);

        logger.info("compiling {}", params);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        final int r = compiler.run(null, null, null, params.toArray(new String[params.size()]));

        if (r == 0) {
            logger.info("compilation OK.");
        } else {
            logger.info("compilation failed.");
        }

        return javaFilesList;
    }
}
