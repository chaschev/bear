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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.junit.Test;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ThresholdRangeFilterTest {
    @Test
    public void testFilter() throws Exception {
        ThresholdRangeFilter filter1 = ThresholdRangeFilter.createFilter("DEBUG", "INFO", null, null);

        assertThat(filter1.filter(Level.TRACE)).isEqualTo(Filter.Result.DENY);
        assertThat(filter1.filter(Level.DEBUG)).isEqualTo(Filter.Result.NEUTRAL);
        assertThat(filter1.filter(Level.INFO)).isEqualTo(Filter.Result.DENY);

        ThresholdRangeFilter filter2 = ThresholdRangeFilter.createFilter("DEBUG", "WARN", null, null);

        assertThat(filter2.filter(Level.TRACE)).isEqualTo(Filter.Result.DENY);
        assertThat(filter2.filter(Level.DEBUG)).isEqualTo(Filter.Result.NEUTRAL);
        assertThat(filter2.filter(Level.INFO)).isEqualTo(Filter.Result.NEUTRAL);
        assertThat(filter2.filter(Level.WARN)).isEqualTo(Filter.Result.DENY);
    }
}
