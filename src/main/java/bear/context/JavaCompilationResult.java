package bear.context;

import bear.main.CompiledEntry;
import com.google.common.base.Optional;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class JavaCompilationResult implements CompilationResult {
    public long timestamp;

    public JavaCompilationResult() {
    }

    public Optional<CompiledEntry> byName(String name){
        CompiledEntry aClass = findEntry(name, scriptClasses);

//        if(aClass != null) return aClass;

//        return findEntry(name, settingsClasses);
        throw new UnsupportedOperationException("todo");
//        return null;
    }

    private static CompiledEntry findEntry(String name, List<CompiledEntry> classes) {
        for (CompiledEntry scriptClass : classes) {
            if(scriptClass.aClass.getName().equals(name) || scriptClass.getName().equals(name)){
                return scriptClass;
            }
        }

        return null;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public Collection<CompiledEntry> getEntries() {
        throw new UnsupportedOperationException("todo JavaCompilationResult.getEntries");
    }

    @Override
    public Optional<CompiledEntry> byFile(File file) {
        throw new UnsupportedOperationException("todo JavaCompilationResult.byFile");
    }

    protected List<CompiledEntry> scriptClasses = new ArrayList<CompiledEntry>();
    protected List<CompiledEntry> settingsClasses = new ArrayList<CompiledEntry>();
}
