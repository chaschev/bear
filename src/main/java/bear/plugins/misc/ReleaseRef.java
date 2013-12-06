package bear.plugins.misc;

import javax.annotation.Nullable;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ReleaseRef {
    @Nullable
    final String path;
    @Nullable
    final String label;

    ReleaseRef(String path, String label) {
        this.path = path;
        this.label = label;
    }

    public static ReleaseRef label(String label){
        return new ReleaseRef(null, label);
    }

    public static ReleaseRef path(String path){
        return new ReleaseRef(path, null);
    }

    public boolean isLabel() {
        return path == null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ReleaseRef{");
        if(path != null) sb.append("path='").append(path).append('\'');
        if(label != null) sb.append("label='").append(label).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
