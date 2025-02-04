package com.elepy.evaluators;

import com.elepy.Base;
import com.elepy.Resource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ObjectUpdateTest extends Base {

    @Test
    public void testSuccessfulUpdate() {
        ObjectUpdateEvaluator<Resource> resourceObjectUpdateEvaluator = new DefaultObjectUpdateEvaluator<>();

        Resource updatedEditable = validObject();
        updatedEditable.setNumberMax40(BigDecimal.valueOf(9));

        Resource resource = validObject();
        resource.setId(updatedEditable.getId());

        assertDoesNotThrow(() -> resourceObjectUpdateEvaluator.evaluate(resource, updatedEditable));
    }

    @Test
    public void testCantChangeNonEditable() {
        ObjectUpdateEvaluator<Resource> resourceObjectUpdateEvaluator = new DefaultObjectUpdateEvaluator<>();

        Resource updatedNonEditable = validObject();
        updatedNonEditable.setNonEditable(UUID.randomUUID().toString());

        assertThrows(Exception.class, () -> resourceObjectUpdateEvaluator.evaluate(validObject(), updatedNonEditable));
    }


}
