package bear.core;

import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

// bear script is not used in the project anymore
@Ignore
public class BearScriptTest {
    @Test
    public void testParse1(){
        BearParserScriptSupplier.BearScriptParseResult parseResult = BearParserScriptSupplier.parseScript("" +
            ":use shell groovyShell\n" +
            ":set x='keke'\n" +
            "abc\n" +
            "def\n" +
            "\n" +
            ":use shell groovy\n" +
            ":set x='lala'\n" +
            "pwd", "initial");

        List<ScriptItem> items = parseResult.scriptItems;

        assertThat(items).hasSize(2);
        assertThat(parseResult.globalErrors).isEmpty();

        assertThat(items.get(0).pluginName).isEqualTo("groovyShell");
        assertThat(items.get(1).pluginName).isEqualTo("groovy");
        assertThat(items.get(0).lines.get(0)).startsWith(":set x").contains("keke");
        assertThat(items.get(1).lines.get(0)).startsWith(":set x").contains("lala");
    }



}
