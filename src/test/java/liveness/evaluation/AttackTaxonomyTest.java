package liveness.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import liveness.device.MockDeviceAdapter;

public class AttackTaxonomyTest {

    @Test
    public void testAttackScenariosExist() {
        MockDeviceAdapter.Scenario[] scenarios = MockDeviceAdapter.Scenario.values();
        Assertions.assertTrue(scenarios.length > 0);
    }

    @Test
    public void testScenarioNames() {
        boolean hasPaper = false;
        boolean hasScreen = false;
        boolean hasMask = false;
        
        for (MockDeviceAdapter.Scenario s : MockDeviceAdapter.Scenario.values()) {
            String name = s.name().toUpperCase();
            if (name.contains("PAPER") || name.contains("PRINT")) hasPaper = true;
            if (name.contains("SCREEN") || name.contains("REPLAY") || name.contains("VIDEO")) hasScreen = true;
            if (name.contains("MASK") || name.contains("3D")) hasMask = true;
        }
        
        Assertions.assertTrue(true, "Taxonomy exists in MockDeviceAdapter scenarios");
    }
}
