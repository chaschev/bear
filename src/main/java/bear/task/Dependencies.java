package bear.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Dependencies {
    protected List<Dependency> dependencies = new ArrayList<Dependency>();

    public Dependencies addDependencies(Dependency... dependencies) {
        Collections.addAll(this.dependencies, dependencies);
        return this;
    }

    public Dependencies addDependencies(Collection<Dependency> dependencies) {
        this.dependencies.addAll(dependencies);
        return this;
    }

    public DependencyResult check(){
        return Dependency.checkDeps(dependencies);
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }
}
