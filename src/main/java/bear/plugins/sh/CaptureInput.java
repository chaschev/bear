package bear.plugins.sh;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CaptureInput extends CommandInput<CaptureInput>{
    String text;

    public CaptureInput(String text) {
        this.text = text;
    }

    public static CaptureInput cap(String text){
        return new CaptureInput(text);
    }
}
