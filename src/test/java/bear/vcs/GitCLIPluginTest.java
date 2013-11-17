package bear.vcs;

import org.junit.Test;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GitCLIPluginTest {
    @Test
    public void testLogParser() throws Exception {
        LogResult apply = GitCLIPlugin.LOG_PARSER.apply("" +
            "\n\ncommit 9e7f01a379b3553f2855ee871f05580e97e07c1a\n" +
            "Author: Jeff Scott Brown <jbrown@gopivotal.com>\n" +
            "Date:   Tue Jun 4 15:18:37 2013 -0700\n" +
            "\n" +
            "    fix javascript\n" +
            "\n" +
            "commit 6977225cbac9d59d48eb56edf5d4f00fc8373bc1\n" +
            "Author: Jeff Brown <jeff@jeffandbetsy.net>\n" +
            "Date:   Thu Jun 21 15:56:50 2012 -0600\n" +
            "\n" +
            "    add grails wrapper" +
            " \n");

        System.out.println(apply);
    }
}
