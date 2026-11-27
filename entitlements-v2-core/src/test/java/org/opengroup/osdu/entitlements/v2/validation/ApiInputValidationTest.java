package org.opengroup.osdu.entitlements.v2.validation;

import org.junit.Assert;
import org.junit.Test;
import org.opengroup.osdu.core.common.model.http.AppException;

public class ApiInputValidationTest {

    @Test
    public void shouldThrowErrorWhenDataPartitionIdDoesNotMatchWithGroupEmail() {
        try {
            ApiInputValidation.validateEmailAndBelongsToPartition("data.x.viewers@common.contoso.com", "common2.contoso.com");
            Assert.fail();
        } catch (AppException e) {
            Assert.assertEquals("Wrong partition domain for email: 'DataPartitionId.Domain' "
                + "pattern should be used. Data Partition Id should match with the group.", e.getError().getMessage());
            Assert.assertEquals(400, e.getError().getCode());
            Assert.assertEquals("Bad Request", e.getError().getReason());
        }
    }

    @Test
    public void shouldValidateEmailSuccessfullyForPartition() {
        try {
            ApiInputValidation.validateEmailAndBelongsToPartition("data.x.viewers@common.contoso.com", "common.contoso.com");
        } catch (AppException e) {
            Assert.fail();
        }
    }

    @Test
    public void shouldThrowErrorWhenNotEmailProvided() {
        try {
            ApiInputValidation.validateEmail("data.x|viewers@comm|on.contoso.com");
        } catch (AppException e) {
            Assert.assertEquals("Invalid email provided", e.getError().getMessage());
            Assert.assertEquals(400, e.getError().getCode());
            Assert.assertEquals("Bad Request", e.getError().getReason());
        }
    }

    @Test
    public void shouldValidateEmailSuccessfully() {
        try {
            ApiInputValidation.validateEmail("data.viewers@common.contoso.com");
        } catch (AppException e) {
            Assert.fail();
        }
    }

    @Test
    public void shouldParseNullOrEmptyCursorAsZero() {
        Assert.assertEquals(0, ApiInputValidation.parseNonNegativeCursorOffset(null));
        Assert.assertEquals(0, ApiInputValidation.parseNonNegativeCursorOffset(""));
    }

    @Test
    public void shouldParseNonNegativeCursor() {
        Assert.assertEquals(0, ApiInputValidation.parseNonNegativeCursorOffset("0"));
        Assert.assertEquals(58800, ApiInputValidation.parseNonNegativeCursorOffset("58800"));
    }

    @Test
    public void shouldRejectNegativeCursor() {
        try {
            ApiInputValidation.parseNonNegativeCursorOffset("-58800");
            Assert.fail();
        } catch (AppException e) {
            Assert.assertEquals("Malformed cursor, must be integer value", e.getError().getMessage());
            Assert.assertEquals(400, e.getError().getCode());
        }
    }

    @Test
    public void shouldRejectNonIntegerCursor() {
        try {
            ApiInputValidation.parseNonNegativeCursorOffset("not-a-number");
            Assert.fail();
        } catch (AppException e) {
            Assert.assertEquals("Malformed cursor, must be integer value", e.getError().getMessage());
            Assert.assertEquals(400, e.getError().getCode());
        }
    }
}
