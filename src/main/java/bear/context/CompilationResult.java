package bear.context;

import bear.main.CompiledEntry;
import com.google.common.base.Optional;

import java.io.File;
import java.util.Collection;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface CompilationResult {
    Optional<? extends CompiledEntry> byName(String name);
    long timestamp();
    Collection<? extends CompiledEntry> getEntries();

    Optional<? extends CompiledEntry> byFile(File file);
}
