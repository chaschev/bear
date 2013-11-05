/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.plugins.groovy;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Replacements {
    public static final Replacements EMPTY = new Replacements(0, 0);

    int start;
    int end;

    Replacements(int start, int end) {
        this.start = start;
        this.end = end;
    }

    List<Replacement> replacements = new ArrayList<Replacement>();

    boolean add(Replacement replacement) {
        return replacements.add(replacement);
    }

    Replacements addAll(List<GroovyCodeCompleter.Candidate> candidates) {
        replacements.addAll(Lists.transform(candidates,
            new Function<GroovyCodeCompleter.Candidate, Replacement>() {
                public Replacement apply(GroovyCodeCompleter.Candidate input) {
                    return input.r;
                }
            }));

        return this;
    }

    public List<Replacement> getReplacements() {
        return replacements;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
