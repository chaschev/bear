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
public abstract class EventToUI extends ObjectMessage{
    protected final String type;
    protected final String subType;
    protected String parentId;
    protected int level = -10;

    public final long timestamp = System.currentTimeMillis();

    public EventToUI(String type) {
        this.type = type;
        this.subType = null;
    }

    protected EventToUI(String type, String subType) {
        this.type = type;
        this.subType = subType;
    }

    public String getType() {
        return type;
    }

    public String getSubType() {
        return subType;
    }

    public EventToUI setParentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public String getParentId() {
        return parentId;
    }

    public Integer getLevel() {
        return level == -10 ? null : level;
    }

    public EventToUI setLevel(int level) {
        this.level = level;
        return this;
    }
}
