package bear.plugins.groovy;

//import antlr.ASTFactory;
//import antlr.collections.AST;

import chaschev.lang.OpenBean;
import chaschev.lang.reflect.MethodDesc;
import com.google.common.collect.Lists;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.antlr.SourceBuffer;
import org.codehaus.groovy.antlr.UnicodeEscapingReader;
import org.codehaus.groovy.antlr.parser.GroovyLexer;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static chaschev.lang.OpenBean.getFieldValue;
import static java.lang.Character.isWhitespace;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GroovyCodeCompleter {
    Binding binding;
    GroovyShell shell;

    static class Candidate implements Comparable<Candidate> {
        Replacement r;
        int score;

        Candidate(Replacement r, int score) {
            this.r = r;
            this.score = score;
        }

        @Override
        public int compareTo(Candidate o) {
            return -Integer.compare(score, o.score);
        }
    }

    static class Token {
        String name;
        boolean method;

        int start;
        int end = -1;

        Token(int start) {
            this.start = start;
        }

        Token(String name, boolean method) {
            this.name = name;
            this.method = method;
        }

        static Token m(String name) {
            return new Token(name, true);
        }

        public String text(String s) {
            return s.substring(start, end);
        }

        static Token f(String name) {
            return new Token(name, false);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Token token = (Token) o;

            if (method != token.method) return false;
            if (name != null ? !name.equals(token.name) : token.name != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (method ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Token{");
            sb.append("name='").append(name).append('\'');
            sb.append(", method=").append(method);
            sb.append(", start=").append(start);
            sb.append(", end=").append(end);
            sb.append('}');
            return sb.toString();
        }
    }

    public GroovyCodeCompleter(GroovyShell shell) {
        this.shell = shell;
        this.binding = (Binding) getFieldValue(shell, "context");
    }

    public GroovyCodeCompleter(Binding binding, GroovyShell shell) {
        this.binding = binding;
        this.shell = shell;
    }

    public Replacements completeCode(String script, int position) {
        int[] range = scanForStart(script, position, -1);

        int start = range[0];
        int end = range[1] + 1;

        boolean endsWithDot = script.charAt(firstNonSpace(script, Math.min(end, script.length() - 1), -1)) == '.';

        List<Token> tokens = tokenize(script, start, end);

        //fo| => size 1
        //foo. => size 1
        Class<?> firstTokenClass;
        if (tokens.size() == 1 && !endsWithDot) {
            firstTokenClass = null;
            //match vars from binding, there should be a few


            Set<Map.Entry<String, ?>> entries = binding.getVariables().entrySet();
            List<Candidate> candidates = new ArrayList<Candidate>();

            for (Map.Entry<String, ?> entry : entries) {
                String varName = entry.getKey();

                char[] chars = tokens.get(0).name.toCharArray();

                int score = 0;

                for (int i = 0; i < chars.length; i++) {
                    score += frequency(varName, chars[i]) * i == 0 ? 3 : (i == 1 ? 2 : 1);
                }

                if (score > 0) {
                    candidates.add(new Candidate(new Replacement(varName, entry.getValue().getClass().getSimpleName()), score));
                }
            }

            Collections.sort(candidates);

            return new Replacements(start, end).addAll(candidates);
        } else {
            firstTokenClass = shell.getVariable(tokens.get(0).name).getClass();
        }

        List<Class<?>> currentClasses = firstTokenClass == null ? new ArrayList<Class<?>>() : Lists.<Class<?>>newArrayList(firstTokenClass);

        for (int i = 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            boolean lastToken = i == tokens.size() - 1;

            if (lastToken && !endsWithDot) {
                break;
            }

            //strict match
            List<Class<?>> returnTypes = new ArrayList<Class<?>>();

            if (token.method) {
                for (Class<?> currentClass : currentClasses) {
                    for (MethodDesc method : OpenBean.methods(currentClass)) {
                        if (method.getName().equals(token.name)) {
                            returnTypes.add(method.getMethod().getReturnType());
                        }
                    }
                }
            } else {
                for (Class<?> currentClass : currentClasses) {
                    for (Field field : OpenBean.fields(currentClass)) {
                        if (field.getName().equals(token.name)) {
                            returnTypes.add(field.getType());
                        }
                    }
                }
            }


            if (returnTypes.size() > 1) {
                currentClasses = Lists.newArrayList(new LinkedHashSet<Class<?>>(returnTypes));
            }else{
                currentClasses = returnTypes;
            }
        }

        String pattern = null;

        if (!endsWithDot) {
            pattern = tokens.get(tokens.size() - 1).name.toLowerCase();
        }

        Replacements replacements = endsWithDot
            ? new Replacements(position, position) :
            new Replacements(tokens.get(tokens.size() - 1).start, position);

        if (endsWithDot) {
            for (Class<?> currentClass : currentClasses) {
                for (Field field : OpenBean.fields(currentClass)) {
                    replacements.add(new Replacement(field));
                }

                for (MethodDesc method : OpenBean.methods(currentClass)) {
                    replacements.add(new Replacement(method));
                }
            }
        } else {
            final String patternLC = pattern.toLowerCase();
            Set<Field> usedFields = new HashSet<Field>();
            Set<Method> usedMethods = new HashSet<Method>();

            List<Candidate> candidates = new ArrayList<Candidate>();

            {
                int score = 10000;
                for (Class<?> currentClass : currentClasses) {
                    for (Field field : OpenBean.fields(currentClass)) {
                        if (field.getName().toLowerCase().startsWith(patternLC)) {
                            usedFields.add(field);
                            candidates.add(new Candidate(new Replacement(field), score--));
                        }
                    }

                    for (MethodDesc method : OpenBean.methods(currentClass)) {
                        if (method.getName().toLowerCase().startsWith(patternLC)) {
                            usedMethods.add(method.getMethod());
                            candidates.add(new Candidate(new Replacement(method), score--));
                        }
                    }
                }
            }

            Collections.sort(candidates, new Comparator<Candidate>() {
                @Override
                public int compare(Candidate o1, Candidate o2) {
                    int d1 = StringUtils.getLevenshteinDistance(o1.r.name.toLowerCase(), patternLC);
                    int d2 = StringUtils.getLevenshteinDistance(o2.r.name.toLowerCase(), patternLC, d1 + 1);
                    return Integer.compare(d1, d2);
                }
            });

            for (int i = 0; i < candidates.size(); i++) {
                Candidate candidate = candidates.get(i);
                candidate.score = 10000 - i;
            }

            char[] chars = pattern.toCharArray();
            for (Class<?> currentClass : currentClasses) {
                for (Field field : OpenBean.fields(currentClass)) {
                    if (usedFields.contains(field)) continue;

                    int r = 0;

                    for (char aChar : chars) {
                        r += frequency(field.getName(), aChar);
                    }

                    if (r > 0)
                        candidates.add(new Candidate(new Replacement(field), r));

                }

                for (MethodDesc method : OpenBean.methods(currentClass)) {
                    if (usedMethods.contains(method.getMethod())) continue;

                    int r = 0;

                    for (char aChar : chars) {
                        r += frequency(method.getName(), aChar);
                    }

                    if (r > 0)
                        candidates.add(new Candidate(new Replacement(method), r));
                }
            }

            Collections.sort(candidates);

            replacements.addAll(candidates);
        }


        return replacements;
    }

    private int frequency(String name, char aChar) {
        int r = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == aChar) r++;
        }

        return r;
    }

    static List<Token> tokenize(String script, int start, int end) {
        Token currentToken;

        int nonSpaceStart = firstNonSpace(script, start, 1);

        List<Token> tokens = Lists.newArrayList(currentToken = new Token(nonSpaceStart));

        char matchingQuote = 0;

        int openedBrackets = 0;
        int closedBrackets = 0;

        for (int pos = nonSpaceStart; pos < end; pos++) {
            char ch = script.charAt(pos);

            //check string mode, copied
            if (ch == '\"' || ch == '\'') {
                matchingQuote = closeOpenQuotes(matchingQuote, ch);

                continue;
            }

            if (matchingQuote != 0) continue;

            if (ch == '(' && !currentToken.method) {
                currentToken.method = true;
                int nonSpace = firstNonSpace(script, pos - 1, -1) + 1;
                currentToken.name = script.substring(currentToken.start, nonSpace);
            }

            final boolean isBracket;

            switch (ch) {
                case ')':
                    closedBrackets++;
                    isBracket = true;
                    break;

                case '(':
                    openedBrackets++;
                    isBracket = true;
                    break;
                default:
                    isBracket = false;
            }

            if (openedBrackets > closedBrackets) {
                continue;
            }

            if (openedBrackets > 0 && openedBrackets == closedBrackets) {
                currentToken.end = pos + 1;
                openedBrackets = 0;
                closedBrackets = 0;
            }

            if (isBracket) continue;


            if (ch != '.' && !Character.isJavaIdentifierPart(ch) && !currentToken.method) {
                //spaces after identifier, in method there can be anything
                currentToken.end = pos;
                currentToken.name = currentToken.text(script);
                continue;
            }

            boolean atEnd = pos >= end - 1;
            boolean isDot = ch == '.';

            if (isDot || atEnd) {
                if (currentToken.end == -1) {  //end is not -1 for a method
                    if (atEnd) {
                        if(isDot){
                            currentToken.end = pos;
                        }else{
                            currentToken.end = end;
                        }
                    }
                    else {
                        currentToken.end = firstNonSpace(script, pos - 1, -1) + 1;
                    }

                    currentToken.name = currentToken.text(script);
                }

                int nonSpace = firstNonSpace(script, pos + 1, 1);

                pos = nonSpace;

                atEnd = pos >= end - 1;

                if (!atEnd) {
                    tokens.add(currentToken = new Token(nonSpace));
                }

                continue;
            }


        }
        return tokens;
    }

    private void groovyParsingSample(String script, int[] range) throws Exception {
        SourceBuffer sourceBuffer = new SourceBuffer();
        String source = script.substring(range[0], range[1]);
        UnicodeEscapingReader unicodeReader = new UnicodeEscapingReader(new StringReader(source), sourceBuffer);
        GroovyLexer lexer = new GroovyLexer(unicodeReader);
        unicodeReader.setLexer(lexer);

//        parseFile("mem", lexer, sourceBuffer, source);
    }

    /**
     * Returning a range because rightBorder(+startPos+) can move left 1 char when placed at the space.
     * todo check if it replaces ok
     * See unit test to check how it works.
     *
     * @param startPos == script.length if at the end of the script.
     */
    static int[] scanForStart(String script, int startPos, final int inc) {
        int l = script.length();

        char matchingQuote = 0;

        int openedBrackets = 0;
        int closedBrackets = 0;

        int pos;

        startPos--; // ' ' or '(' are not our characters

        for (pos = startPos; pos >= 0 && pos < l; pos += inc) {

            char ch = script.charAt(pos);

            //check string mode, copied
            if (ch == '\"' || ch == '\'') {
                matchingQuote = closeOpenQuotes(matchingQuote, ch);

                continue;
            }

            if (matchingQuote != 0) continue;

            //normal mode
            final boolean isBracket;

            // foo2.recursiveFoo(xx()) - here the invariant is:  closed>=opened
            // outerMethod|( foo2.recursiveFoo(xx()) here it breaks
            switch (ch) {
                case ')':
                    closedBrackets++;
                    isBracket = true;
                    break;

                case '(':
                    openedBrackets++;
                    isBracket = true;
                    break;
                default:
                    isBracket = false;
            }

            // this case: outerMethod|( foo2.recursiveFoo(xx()) here it breaks
            if (openedBrackets > closedBrackets) {
                return new int[]{firstNonSpace(script, pos + 1, 1), startPos};
            }

            if (openedBrackets < closedBrackets) {
                continue;
            }

            if (isBracket) continue;

            if (Character.isJavaIdentifierPart(ch) || ch == '.') continue;

            break;
        }

        pos -= inc;
//        if(pos < 0) pos = 0;
//        if(pos >= l) pos = l-1;

        return new int[]{pos, startPos};
    }

    private static char closeOpenQuotes(char matchingQuote, char ch) {
        if (matchingQuote == ch) {
            matchingQuote = 0;
        } else if (matchingQuote == 0) {
            matchingQuote = ch;
        } else {
            //just skip
        }
        return matchingQuote;
    }

    private static int firstNonSpace(String script, int pos, int inc) {
        for (; pos < script.length() && pos >= 0 && isWhitespace(script.charAt(pos));
             pos += inc) {
        }

        return pos;
    }

    // Here's where we do the real work...
    //needs groovy, not groovy-all
    /*public static void parseFile(String f, GroovyLexer l, SourceBuffer sourceBuffer, String source)
        throws Exception {
        try {
            // Create a parser that reads from the scanner
            final GroovyRecognizer parser = GroovyRecognizer.make(l);
            parser.setSourceBuffer(sourceBuffer);
            parser.setFilename(f);

            if (false) {
                GroovyLexer lexer = parser.getLexer();
                lexer.setWhitespaceIncluded(true);
                while (true) {
                    antlr.Token t = lexer.nextToken();
                    System.out.println(t);
                    if (t == null || t.getType() == antlr.Token.EOF_TYPE) break;
                }
                return;
            }

            // start parsing at the compilationUnit rule
            parser.compilationUnit();

            AST ast = parser.getAST();
            System.out.println("parseFile " + f + " => " + ast);

            ASTFactory factory = new ASTFactory();
            AST r = factory.create(0, "AST ROOT");

            r.setFirstChild(ast);

            GroovySourceAST groovySourceAST = new GroovySourceAST();
            groovySourceAST.initialize(ast);

            Visitor visitor = new VisitorAdapter() {
                int level = -1;

                @Override
                public void visitDefault(GroovySourceAST t, int visit) {
                    if (visit == OPENING_VISIT) {
                        level++;
                        System.out.print(StringUtils.repeat(' ', level * 2) + "opening ");
                    }

                    if (visit == CLOSING_VISIT) {
                        System.out.print(StringUtils.repeat(' ', level * 2) + "closing ");
                        level--;
                    }
                    System.out.print(parser.getTokenName(t.getType()) + " ");

                    System.out.println(t.toString());
//                    System.out.println(t.getSnippet());
                }
            };
            AntlrASTProcessor treewalker = new SourceCodeTraversal(visitor);

            treewalker.process(ast);

            System.out.println(ast);

        } catch (Exception e) {
            System.err.println("parser exception: " + e);
            e.printStackTrace();   // so we can get stack trace
        }
    }*/
}
