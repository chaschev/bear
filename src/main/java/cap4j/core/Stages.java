package cap4j.core;

import java.util.ArrayList;
import java.util.List;

/**
 * User: achaschev
 * Date: 8/5/13
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

