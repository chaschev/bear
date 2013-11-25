package bear.core;

import com.google.common.base.Optional;
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

        assertThat(p(":set var1 {\"value\":true, \"global\": true, \"save\":true}").params).contains(entry("value", true));

        BearScript2.BearScriptDirective d1 = p(":set var1 {\"value\":\"val\", \"global\": true, \"save\":true}");
        BearScript2.BearScriptDirective d2 = p(":set var2 {\"groovy\":\"expression\"}");

        assertThat(d1.params).contains(entry("value", "val"));
        assertThat(d2.params).contains(entry("groovy", "expression"));

        assertThat(new BearScript2.ScriptItem(Optional.of("var1"), "groovy", 0).addVariable("var1", d1).variables.get().values())
            .contains(new BearScript2.ScriptSetVariable("var1", "val", null, true, false, true));
        assertThat(new BearScript2.ScriptItem(Optional.of("var2"), "groovy", 0).addVariable("var2", d2).variables.get().values())
            .contains(new BearScript2.ScriptSetVariable("var2", null, "expression", false, false, false));

        assertThat(p(":ref {}").directive).isEqualTo(":ref");
        assertThat(p(":ref {}").words).isEmpty();
        assertThat(p(":ref {\"file\": \"c:/blah blah\"}").params).contains(entry("file", "c:/blah blah"));
        assertThat(p(":ref {\"url\": \"c:/blah blah\"}").params).contains(entry("url", "c:/blah blah"));

        assertThat(p("//!:use plugin groovy {\"name\":\"Bob\"}").params).contains(entry("name", "Bob"));
    }

    private static BearScript2.BearScriptDirective p(String line) {
        return new DirectiveParser().parse(line);
    }
}
