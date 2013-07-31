package cap4j.session;

/**
* User: chaschev
* Date: 7/29/13
*/
public class SshAddress {
    String username;
    String password;
    String address;

    public SshAddress(String username, String password, String address) {
        this.username = username;
        this.password = password;
        this.address = address;
    }
}
