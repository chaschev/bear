package bear.main;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class Compiler {
    protected final String[] extensions = extensions();

    protected File sourcesDir;
    protected File buildDir;

    public abstract String[] extensions();

    Compiler(File sourcesDir, File buildDir) {
        this.sourcesDir = sourcesDir;
        this.buildDir = buildDir;
    }

    public boolean accepts(String extension) {
        return ArrayUtils.contains(extensions, extension);
    }

    public abstract BearCommandLineConfigurator.CompilationResult compile();
}
