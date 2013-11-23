package bear.main.event;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class NoticeEventToUI extends EventToUI {
    public long timestamp = System.currentTimeMillis();
    public String title;
    public String message;

    public NoticeEventToUI(String title, String message) {
        super("notice", "");
        this.title = title;
        this.message = message;
    }
}
