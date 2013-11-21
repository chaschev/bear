package bear.core;

import org.junit.Test;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.entry;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DirectiveParserTest {
    @Test
    public void testParse() throws Exception {
        assertThat(p(":use plugin groovy").directive).isEqualTo(":use");
        assertThat(p(":use plugin groovy").words).containsExactly("plugin", "groovy");
        assertThat(p(":use plugin groovy").params).isNull();

        assertThat(p(":use plugin groovy {}").directive).isEqualTo(":use");
        assertThat(p(":use plugin groovy {}").words).containsExactly("plugin", "groovy");
        assertThat(p(":use plugin groovy {}").params).isEmpty();

        assertThat(p(":use plugin groovy {\"name\":\"Bob\"}").params).contains(entry("name", "Bob"));

        assertThat(p(":set var {\"value\":\"val\"}").params).contains(entry("value", "val"));
        assertThat(p(":set gvar {\"groovy\":\"expression\"}").params).contains(entry("groovy", "expression"));

        assertThat(p(":ref {}").directive).isEqualTo(":ref");
        assertThat(p(":ref {}").words).isEmpty();
        assertThat(p(":ref {\"file\": \"c:/blah blah\"}").params).contains(entry("file", "c:/blah blah"));
        assertThat(p(":ref {\"url\": \"c:/blah blah\"}").params).contains(entry("url", "c:/blah blah"));
    }

    private static BearScript2.BearScriptDirective p(String line) {
        return new DirectiveParser().parse(line);
    }
}
