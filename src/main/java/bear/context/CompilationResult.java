package bear.context;

import bear.main.CompiledEntry;
import com.google.common.base.Optional;

import java.io.File;
import java.util.Collection;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface CompilationResult {
    Optional<CompiledEntry> byName(String name);
    long timestamp();
    Collection<CompiledEntry> getEntries();

    Optional<CompiledEntry> byFile(File file);
}
