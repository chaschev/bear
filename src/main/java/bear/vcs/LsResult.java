package bear.vcs;

import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public final class LsResult extends CommandLineResult<LsResult> {
    List<String> files;

    public LsResult() {
    }

    public LsResult(String text, List<String> files) {
        super("ls", text);
        this.files = files;
    }

    public List<String> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LsResult{");
        sb.append("files=").append(files);
        sb.append('}');
        return sb.toString();
    }
}
