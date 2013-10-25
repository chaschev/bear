package bear.main;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.io.File;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CompileManager {
    protected final File sourcesDir;
    protected final File buildDir;
    protected final JavaCompiler2 javaCompiler;
    BearCommandLineConfigurator.CompilationResult lastCompilationResult;

    public CompileManager(File sourcesDir, File buildDir) {
        this.sourcesDir = sourcesDir;
        this.buildDir = buildDir;
        this.javaCompiler = new JavaCompiler2(sourcesDir, buildDir);
    }

    public BearCommandLineConfigurator.CompilationResult compileWithAll() {
        BearCommandLineConfigurator.logger.info("compiling...");
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

    public CompiledEntry findClass(final String className, boolean script) {
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
