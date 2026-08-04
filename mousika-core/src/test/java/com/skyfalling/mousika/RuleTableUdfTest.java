package com.skyfalling.mousika;

import com.skyfalling.mousika.udf.RuleTableUdf;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleTableUdfTest {

    @Test
    void nullInputValuesUseTheEmptyCellMatchingRule() {
        RuleTableUdf ruleTable = RuleTableUdf.fromJson("""
                {
                  "keys": ["region"],
                  "values": ["decision"],
                  "data": [["", "default"]]
                }
                """);
        Map<String, Object> target = new HashMap<>();
        target.put("region", null);

        assertEquals(Map.of("decision", "default"), ruleTable.apply(target));
        assertEquals(Map.of("decision", "default"), ruleTable.apply(null));
    }

    @Test
    void rejectsRowsWhoseColumnCountDoesNotMatchTheDefinition() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RuleTableUdf.fromJson("""
                        {
                          "keys": ["region"],
                          "values": ["decision"],
                          "data": [["cn"]]
                        }
                        """));

        assertEquals("decision table row 0 must contain 2 columns", exception.getMessage());
    }
}
