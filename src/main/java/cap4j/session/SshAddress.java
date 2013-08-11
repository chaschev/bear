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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("username='").append(username).append('\'');
        sb.append(", address='").append(address).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
