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

package cap4j.core;

import java.util.Collections;
import java.util.List;

/**
* User: achaschev
* Date: 8/5/13
*/
public class Releases {
    List<String> releases;

    public Releases(List<String> releases) {
        this.releases = releases;
    }

    public String last() {
        return releases.get(releases.size() - 1);
    }

    public String previous() {
        return releases.get(releases.size() - 2);
    }

    public List<String> listToDelete(int keepX) {
        if(releases.size() <= keepX) return Collections.emptyList();

        return releases.subList(0, releases.size() - keepX);
    }
}
