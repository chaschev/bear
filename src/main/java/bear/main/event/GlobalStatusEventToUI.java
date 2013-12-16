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

package bear.main.event;

import bear.core.GlobalTaskRunner;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalStatusEventToUI extends EventToUI {
    public GlobalTaskRunner.Stats stats;

    public GlobalStatusEventToUI(GlobalTaskRunner.Stats stats) {
        super("status", "global");

        this.stats = stats;
    }

    @Override
    public String getFormattedMessage() {
        return "global stats: " + stats;
    }
}
