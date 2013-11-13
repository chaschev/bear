package bear.core;

import org.junit.Test;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class StageTest {

    public static final Role db = role("db");
    public static final Role web = role("web");
    private final Stages stages = sample1();
    private final GlobalContext global = new GlobalContext();
    private final Bear bear = global.bear;

    @Test
    public void testAddressesForStage() throws Exception {
        //default case
//        check("2", "vm1, vm2, vm3, vm4");

        global.putConst(bear.activeHosts, newArrayList("vm1", "vm3"));

        try {
            check("3", "");
            fail("exception must be thrown");
        } catch (Exception e) {
            assertThat(e)
                .isInstanceOf(Stage.StagesException.class)
                .hasNoCause()
                .hasMessageContaining("exist")
                .hasMessageContaining("stage '3'")
                .hasMessageContaining("vm1")
            ;
        }

        check("2", "vm1, vm3");

        global.putConst(bear.activeRoles, newArrayList("db"));

        check("2", "vm1, vm2, vm3");

        global.removeConst(bear.activeRoles);
        global.removeConst(bear.activeHosts);

        check("2", "vm1, vm2, vm3, vm4");
        check("3", "vm2");

        global.putConst(bear.activeRoles, newArrayList("web"));

        check("2", "vm1, vm2, vm3");
        check("3", "vm2");
    }

    @Test(expected = Stage.StagesException.class)
    public void wrongTag() {
        global.putConst(bear.activeRoles, newArrayList("crab"));

        check("2", "vm1, vm2, vm3");

        global.removeConst(bear.activeRoles);
    }

    @Test(expected = Stage.StagesException.class)
    public void wrongHost() {
        global.putConst(bear.activeHosts, newArrayList("vm1001"));

        check("2", "vm1001");
    }

    private void check(String stageName, String csvNames) {
        assertThat(global.var(global.bear.addressesForStage).apply(stages.findByName(stageName))).containsAll(
            stages.hosts(csvNames)
        );
    }

    @Test
    public void testStageCreation() {
        Stages stages = sample1();

        assertThat(stages.getRoles("vm1")).contains(web);
        assertThat(stages.getRoles("vm2")).contains(db, web);
        assertThat(stages.getRoles("vm3")).contains(web);
        assertThat(stages.getRoles("vm4")).isEmpty();
    }

    private Stages sample1() {
        Stages stages = new Stages(global);

        stages
            .add(new Stage("1")
                .addHosts(stages.hosts("vm1, vm2, vm3")))
            .add(new Stage("2")
                .addHosts(stages.hosts("vm1, vm2, vm3, vm4")))
            .add(new Stage("3")
                .addHosts(stages.hosts("vm2")))
        ;

        stages
            .assignRoleToStage(web, "1")
            .assignRoleToHosts(db, "vm2");

        return stages;
    }

    private static Role role(String s) {
        return new Role(s);
    }
}
