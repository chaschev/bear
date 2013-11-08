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
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * This filter returns the onMatch result if the level in the LogEvent is the same or more specific
 * than the configured level and the onMismatch value otherwise. For example, if the ThresholdFilter
 * is configured with Level ERROR and the LogEvent contains Level DEBUG then the onMismatch value will
 * be returned since ERROR events are more specific than DEBUG.
 *
 * The default Level is ERROR.
 */
@Plugin(name = "ThresholdFilter", category = "Core", elementType = "filter", printObject = true)
public final class ThresholdRangeFilter extends AbstractFilter {

    private final Level from;
    private final Level to;

    private ThresholdRangeFilter(Level from, Level to, final Result onMatch, final Result onMismatch) {
        super(onMatch, onMismatch);
        this.from = from;
        this.to = to;
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker, final String msg,
                         final Object... params) {
        return filter(level);
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker, final Object msg,
                         final Throwable t) {
        return filter(level);
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker, final Message msg,
                         final Throwable t) {
        return filter(level);
    }

    @Override
    public Result filter(final LogEvent event) {
        return filter(event.getLevel());
    }

    Result filter(final Level level) {
        return level.intLevel() > to.intLevel() && level.intLevel() <= from.intLevel() ? onMatch : onMismatch;
    }

    @Override
    public String toString() {
        return "[" + from + ", " + to + ")";
    }

    /**
     * Create a ThresholdFilter.
     * @param match The action to take on a match.
     * @param mismatch The action to take on a mismatch.
     * @return The created ThresholdFilter.
     */
    @PluginFactory
    public static ThresholdRangeFilter createFilter(
            @PluginAttribute("levelFrom") final String levelFrom,
            @PluginAttribute("levelTo") final String levelTo,
            @PluginAttribute("onMatch") final String match,
            @PluginAttribute("onMismatch") final String mismatch) {
        final Level from = Level.toLevel(levelFrom, Level.ERROR);
        final Level to = Level.toLevel(levelTo, Level.ERROR);
        final Result onMatch = Result.toResult(match, Result.NEUTRAL);
        final Result onMismatch = Result.toResult(mismatch, Result.DENY);
        return new ThresholdRangeFilter(from, to, onMatch, onMismatch);
    }


}
