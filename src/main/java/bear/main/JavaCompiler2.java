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

import bear.core.IBearSettings;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.commons.io.filefilter.SuffixFileFilter;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static org.apache.commons.io.FilenameUtils.getBaseName;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class JavaCompiler2 extends Compiler {
    private URLClassLoader classLoader;

    JavaCompiler2(File sourcesDir, File buildDir) {
        super(sourcesDir, buildDir);
    }

    @Override
    public String[] extensions() {
        return new String[]{"java"};
    }

    public FXConf.CompilationResult compile() {
        final List<File> filesList = compileScripts(sourcesDir);

        try {
            classLoader = new URLClassLoader(new URL[]{buildDir.toURI().toURL()});
        } catch (MalformedURLException e) {
            throw Exceptions.runtime(e);
        }

        FXConf.CompilationResult result = new FXConf.CompilationResult();

        for (File file : filesList) {
            try {
                Class aClass = (Class) classLoader.loadClass(getBaseName(file.getName()));

                if (Script.class.isAssignableFrom(aClass)) {
                    result.scriptClasses.add(new CompiledEntry(aClass, file, "java"));
                } else if (IBearSettings.class.isAssignableFrom(aClass)) {
                    result.settingsClasses.add(new CompiledEntry(aClass, file, "java"));
                }

            } catch (ClassNotFoundException e) {
                throw Exceptions.runtime(e);
            }
        }

        result.timestamp = System.currentTimeMillis();

        return result;
    }

    public List<File> compileScripts(File sourcesDir) {
        FileFilter filter = new SuffixFileFilter(extensions);

        final File[] files = sourcesDir.listFiles(filter);

        final ArrayList<String> params = newArrayListWithExpectedSize(files.length);

        if (!buildDir.exists()) {
            buildDir.mkdir();
        }

        Collections.addAll(params, "-d", buildDir.getAbsolutePath());
        final List<File> filesList = newArrayList(files);

        final List<String> filePaths = Lists.transform(filesList, new Function<File, String>() {
            public String apply(File input) {
                return input.getAbsolutePath();
            }
        });

        params.addAll(filePaths);

        Cli.logger.info("compiling {}", params);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        final int r = compiler.run(null, null, null, params.toArray(new String[params.size()]));

        if (r == 0) {
            Cli.logger.info("compilation OK.");
        } else {
            Cli.logger.info("compilation failed.");
        }

        return filesList;
    }
}
