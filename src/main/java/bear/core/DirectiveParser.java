package bear.core;

import chaschev.json.JacksonMapper;
import chaschev.json.Mapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.StringTokenizer;

import static com.google.common.base.Optional.fromNullable;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class DirectiveParser {
    static final Mapper mapper = new JacksonMapper();

    static final Map<String, Integer> rules = new ImmutableMap.Builder<String, Integer>()
        .put(":ref", 0)   // means that :use has 0 arguments
        .put(":use", 2)   // :use shell(1) groovy(2)
        .put(":set", 1)   // :set varName(1) {value:xxx}
        .build();

    public BearParserScriptSupplier.BearScriptDirective parse(String line){
        StringTokenizer tokenizer = new StringTokenizer(line);

        String directive = tokenizer.nextToken();

        if(directive.startsWith("//!")){
            directive = directive.substring("//!".length());
        }

        Integer wordCount = rules.get(directive);

        int wordArrayLength = 1;

        if(wordCount != null){
            wordArrayLength = wordCount;
        }

        String[] words = new String[wordArrayLength];

        for (int i = 0; i < words.length; i++) {
             words[i] = tokenizer.nextToken();
        }

        String probableJSONObject = null;

        if(tokenizer.hasMoreElements()){
            probableJSONObject = tokenizer.nextToken("").trim();
        }

        Map<String, Object> params = null;

        if(probableJSONObject != null && probableJSONObject.contains("{")){
            params = mapper.toMap(probableJSONObject);
        }

        Optional<String> name = params == null ? Optional.<String>absent() : fromNullable((String)params.get("name"));

        return new BearParserScriptSupplier.BearScriptDirective(directive, name, words, params);
    }
}
