package bear.main;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TextConsoleEventToUI extends ConsoleEventToUI {
    public String textAdded;

    public TextConsoleEventToUI(String console, String textAdded) {
        super(console, "textAdded");
        this.textAdded = textAdded;
    }
}
