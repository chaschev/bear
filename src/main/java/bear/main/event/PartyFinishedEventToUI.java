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

import bear.task.TaskResult;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PartyFinishedEventToUI extends ConsoleEventToUI {
    public long duration;
    public final TaskResult<?> result;
    public final TaskResult<?> rollbackResult;

    public PartyFinishedEventToUI(String console, long duration, TaskResult<?> result, TaskResult<?> rollbackResult) {
        super(console, "partyFinished");

        this.duration = duration;
        this.result = result;
        this.rollbackResult = rollbackResult;
    }

    @Override
    public String getFormattedMessage() {
        return "partyFinished, result=" + result + ", duration: " + String.format("%.2fs", duration *1.0 / 1000);
    }
}
