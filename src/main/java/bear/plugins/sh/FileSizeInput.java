package bear.plugins.sh;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class FileSizeInput extends CommandInput<FileSizeInput>{
    String path;
    boolean humanReadable;

    public FileSizeInput(String path) {
        this.path = path;
    }

    public static FileSizeInput newSize(String path){
        return new FileSizeInput(path);
    }
}
