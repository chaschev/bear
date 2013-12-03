package bear.plugins.misc;

import bear.console.ConsoleCallback;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WatchDogInput {
    String path;
    boolean sudo;
    int lines = 100;
    int timeoutMs = -1;
    ConsoleCallback callback;

    public WatchDogInput(String path, boolean sudo, ConsoleCallback callback) {
        this.path = path;
        this.sudo = sudo;
        this.callback = callback;
    }

    public WatchDogInput setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public WatchDogInput setLines(int lines) {
        this.lines = lines;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("WatchDogInput{");
        sb.append("path='").append(path).append('\'');
        sb.append(", sudo=").append(sudo);
        sb.append(", lines=").append(lines);
        sb.append(", timeoutMs=").append(timeoutMs);
        sb.append('}');
        return sb.toString();
    }
}
