package cap4j.scm;

/**
* User: achaschev
* Date: 8/4/13
* Time: 3:52 PM
*/
public class BranchInfoResult extends CommandLineResult {
    public String author;
    public String revision;
    public String date;

    public BranchInfoResult(String author, String revision, String date) {
        this.author = author;
        this.revision = revision;
        this.date = date;
    }
}
