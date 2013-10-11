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

package bear.core;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Stages {
    List<Stage> stages = new ArrayList<Stage>();

    public int size() {
        return stages.size();
    }

    public Stages add(Stage stage) {
        stages.add(stage);
        return this;
    }

    public List<Stage> getStages() {
        return stages;
    }


    public void findRemote() {


    }
}
