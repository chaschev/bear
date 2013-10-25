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

    public BearCommandLineConfigurator.CompilationResult compile() {
        final List<File> filesList = compileScripts(sourcesDir);

        try {
            classLoader = new URLClassLoader(new URL[]{buildDir.toURI().toURL()});
        } catch (MalformedURLException e) {
            throw Exceptions.runtime(e);
        }

        BearCommandLineConfigurator.CompilationResult result = new BearCommandLineConfigurator.CompilationResult();

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

        BearCommandLineConfigurator.logger.info("compiling {}", params);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        final int r = compiler.run(null, null, null, params.toArray(new String[params.size()]));

        if (r == 0) {
            BearCommandLineConfigurator.logger.info("compilation OK.");
        } else {
            BearCommandLineConfigurator.logger.info("compilation failed.");
        }

        return filesList;
    }
}
