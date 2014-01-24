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

package bear.main;

import com.google.common.collect.Lists;
import org.junit.Test;

import static bear.main.ProjectGenerator.toCamelHumpCase;
import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ProjectGeneratorTest {
    @Test
    public void testToCamelHumpCase() throws Exception {
        assertThat(toCamelHumpCase("a-b")).isEqualTo("AB");
        assertThat(toCamelHumpCase("a-bc")).isEqualTo("ABc");
        assertThat(toCamelHumpCase("ab-bc")).isEqualTo("AbBc");
        assertThat(toCamelHumpCase("ab-bc-de")).isEqualTo("AbBcDe");
    }

    @Test
    public void testGenerate() throws Exception {
        System.out.println(new ProjectGenerator("drywall-demo", "andrey", "pass", Lists.newArrayList("h1", "h2"), Lists.newArrayList("java")).processTemplate("TemplateProject.template"));

        System.out.println("--------------");

        System.out.println(new ProjectGenerator("drywall-demo", "andrey",  "pass", Lists.newArrayList("h1", "h2"), Lists.newArrayList("java")).generatePom("drywall-demo"));
    }
}
