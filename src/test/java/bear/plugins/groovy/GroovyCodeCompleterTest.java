package bear.plugins.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.fest.assertions.api.Assertions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static bear.plugins.groovy.GroovyCodeCompleter.Token.f;
import static bear.plugins.groovy.GroovyCodeCompleter.Token.m;
import static chaschev.lang.Lists2.projectField;
import static chaschev.lang.OpenBean.fieldNames;
import static chaschev.lang.OpenBean.methodNames;
import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GroovyCodeCompleterTest {
    public static class Foo{
        String name;

        public Foo(String name) {
            this.name = name;
        }

        private String stringField = "s";
        private List arrayListField = new ArrayList();

        private Foo recursiveFoo = this;

        public Foo recursiveFoo(){
            return recursiveFoo;
        }

        public String stringField(){
            return stringField;
        }

        public List getArrayList() {
            return arrayListField;
        }
    }

    @Test
    public void testCompleteCode() throws Exception {
        expectNames("fo|", "foo", "foo1", "foo2");
        expectNames("fo|o", "foo", "foo1", "foo2");
        expectNames("foo|", "foo", "foo1", "foo2");
        expectNames("foo|o", "foo", "foo1", "foo2");
        expectNames("foo1|", "foo1");
        expectNames("foo2|", "foo2");

        expectNames("foo2.arrayListField.is|", "isEmpty", "listIterator", "listIterator", "subList");

        expectMembers("foo2.|", Foo.class);
        expectMembers("foo2.foo.|", Foo.class);
        expectMembers("foo2.recursiveFoo().|", Foo.class);
        expectMembers("foo.recursiveFoo().recursiveFoo().|", Foo.class);
        expectMembers("foo.stringField().|", String.class);
        expectMembers("foo.arrayListField.|", ArrayList.class);
    }

    @Test
    public void testTokenize() throws Exception {
        checkTokenizing("foo2 ", newArrayList(f("foo2")));

        checkTokenizing("foo2", newArrayList(f("foo2")));
        checkTokenizing("foo2 ", newArrayList(f("foo2")));
        checkTokenizing(" foo2", newArrayList(f("foo2")));
        checkTokenizing(" foo2 ", newArrayList(f("foo2")));
        checkTokenizing("foo()", newArrayList(m("foo")));
        checkTokenizing("foo2.foo", newArrayList(f("foo2"), f("foo")));
        checkTokenizing("foo2. foo", newArrayList(f("foo2"), f("foo")));
        checkTokenizing("foo2 .foo", newArrayList(f("foo2"), f("foo")));
        checkTokenizing("foo2 . foo", newArrayList(f("foo2"), f("foo")));
        checkTokenizing(" foo2 . foo ", newArrayList(f("foo2"), f("foo")));
        checkTokenizing(" foo2 . foo ( ) ", newArrayList(f("foo2"), m("foo")));
        checkTokenizing(" foo2 . foo () ", newArrayList(f("foo2"), m("foo")));
        checkTokenizing("foo2.foo()", newArrayList(f("foo2"), m("foo")));
        checkTokenizing(" foo2 . foo ( 's1', 's2! ' ) ", newArrayList(f("foo2"), m("foo")));
        checkTokenizing("foo2.foo(foo1('s1'), 's2').foo2('s3')",
            newArrayList(f("foo2"), m("foo"), m("foo2")));

        checkTokenizing("foo2.foo(foo1('s1'), 's2', ' sdf !\"df!!dd').foo2('s3').foo3(foo1(foo2('s2')), crap).foo",
            newArrayList(f("foo2"), m("foo"), m("foo2"), m("foo3"), f("foo")));
    }

    private void checkTokenizing(String script, List<GroovyCodeCompleter.Token> expected) throws Exception {
        List<GroovyCodeCompleter.Token> tokens = GroovyCodeCompleter.tokenize(script, 0, script.length());

        Assertions.assertThat(tokens).isEqualTo(expected);
    }

    private void expectMembers(String script, Class<?> aClass) {
        assertThat(getNames(script)).containsAll(fieldNames(aClass));
        assertThat(getNames(script)).containsAll(methodNames(aClass));
    }

    private List<String> getNames(String s) {
        throw new UnsupportedOperationException("todo GroovyCodeCompleterTest.getNames");
    }

    private void expectNames(String script, String... names) {
        int caretAt = script.indexOf("|");

        script = script.replace("|", "");

        GroovyShell shell = sampleShell1();

        Replacements replacements = new GroovyCodeCompleter(shell).completeCode(script, caretAt);

        assertThat(projectField(replacements.replacements, Replacement.class, String.class, "name")).contains(names);
    }

    private static GroovyShell sampleShell1() {
        Binding binding = new Binding();

        binding.setVariable("foo", new Foo("foo"));
        binding.setVariable("foo1", new Foo("foo1"));
        binding.setVariable("foo2", new Foo("foo2"));

        return new GroovyShell(binding);
    }

    @Test
    public void testScanForStart() throws Exception {
        checkStart("^foo2.| x");

        checkStart("^foo2()|.");
        checkStart("^foo2()|.");
        checkStart("^foo2('  ')|.");
        checkStart("^foo2('\"')|.");
        checkStart("^foo2('\"').foo3(\"'  hi   '\")|.");
        checkStart("^foo2|.");
        checkStart("^foo2.|");
        checkStart("^foo2(xx()).|");
        checkStart("^foo2(xx(' ! ')).|");
        checkStart("^foo2.recursiveFoo()|");
        checkStart("^foo2.recursiveFoo(xx())|");
        checkStart("^foo2.foo|('s1', 's2').");
    }

    private void checkStart(String s) {
        checkStart(s, true);
    }

    private void checkStart(String s, boolean addChecks) {
        int expectedStart = s.indexOf("^");
        int caretPos = s.indexOf("|");

        if(caretPos == s.length() - 1){
            caretPos--;
        }

        if(expectedStart == s.length() - 1){
            expectedStart--;
        }

        if(expectedStart < caretPos) {
            caretPos--;
        }else{
            expectedStart--;
        }

        String input = s.replace("^","").replace("|", "");

//        if(caretPos == input.length()) caretPos--;
//        if(expectedStart == input.length()) expectedStart--;

        Assertions.assertThat(GroovyCodeCompleter.scanForStart(input, caretPos, -1)[0]).isEqualTo(expectedStart);

        if(addChecks && s.startsWith("^")){
            checkStart("blah-blah " + s, false);
        }

        if(addChecks){
            checkStart("blah-blah " + s + " blah-blah", false);
        }
    }
}
