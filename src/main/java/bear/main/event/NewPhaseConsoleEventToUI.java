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

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class NewPhaseConsoleEventToUI extends ConsoleEventToUI implements EventWithId {
    public final String id;
    public String phaseId;

    public NewPhaseConsoleEventToUI(String console, String id) {
        super(console, "newPhase");
        this.id = id;
    }

     public NewPhaseConsoleEventToUI(String console, String id, String phaseId) {
        super(console, "newPhase");
        this.id = id;
        this.phaseId = phaseId;
    }

    @Override
    public String getFormattedMessage() {
        return "ui: newPhase, id=" + id + ", phaseId=" + phaseId;
    }

    public String getId() {
        return id;
    }
}
