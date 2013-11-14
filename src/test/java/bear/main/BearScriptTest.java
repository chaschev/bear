package bear.main;

import org.junit.Test;

import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearScriptTest {
    @Test
    public void testParse1(){
        BearScript.BearScriptParseResult parseResult = BearScript.parseScript("" +
            ":use shell groovyShell\n" +
            ":set x='keke'\n" +
            "abc\n" +
            "def\n" +
            "\n" +
            ":use shell groovy\n" +
            ":set x='lala'\n" +
            "pwd", "initial");

        List<BearScript.ScriptItem> items = parseResult.scriptItems;

        assertThat(items).hasSize(2);
        assertThat(parseResult.globalErrors).isEmpty();

        assertThat(items.get(0).pluginName).isEqualTo("groovyShell");
        assertThat(items.get(1).pluginName).isEqualTo("groovy");
        assertThat(items.get(0).lines.get(0)).startsWith(":set x").contains("keke");
        assertThat(items.get(1).lines.get(0)).startsWith(":set x").contains("lala");
    }



}
