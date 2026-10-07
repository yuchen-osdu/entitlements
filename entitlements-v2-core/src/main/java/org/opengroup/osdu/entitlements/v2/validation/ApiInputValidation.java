package org.opengroup.osdu.entitlements.v2.validation;

import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.http.HttpStatus;

public class ApiInputValidation {

    /**
     * Shared with OpenAPI so Schemathesis positive generation matches runtime checks.
     * Must stay aligned with {@link #validateEmail(String)}.
     */
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]{1,256}@[A-Za-z0-9+_.-]{1,256}$";

    /**
     * Non-negative integer cursor, or empty (same as omitted → offset {@code 0}).
     * Shared with OpenAPI {@code GET /groups/all} query param {@code cursor}
     * and {@link #parseNonNegativeCursorOffset(String)}.
     */
    public static final String CURSOR_PATTERN = "^[0-9]*$";

    private static final String MALFORMED_CURSOR_MESSAGE = "Malformed cursor, must be integer value";

    private ApiInputValidation() {
        //Instance should be created
    }

    public static void validateEmailAndBelongsToPartition(String groupEmail, String partitionDomain) {
        validateEmail(groupEmail);
        if (!groupEmail.endsWith("@" + partitionDomain)) {
      throw new AppException(
          HttpStatus.BAD_REQUEST.value(),
          HttpStatus.BAD_REQUEST.getReasonPhrase(),
          "Wrong partition domain for email: 'DataPartitionId.Domain' pattern should be used. "
              + "Data Partition Id should match with the group.");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || !email.matches(EMAIL_PATTERN)) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), "Invalid email provided");
        }
    }

    /**
     * Parses a pagination cursor into a non-negative offset.
     * Null/blank means offset {@code 0}. Values that are not non-negative integers are 400.
     * OpenAPI {@code @Schema(pattern)} is documentation-only and does not enforce this at runtime.
     */
    public static int parseNonNegativeCursorOffset(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        if (!cursor.matches(CURSOR_PATTERN)) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(), MALFORMED_CURSOR_MESSAGE);
        }
        try {
            return Integer.parseInt(cursor);
        } catch (NumberFormatException e) {
            throw new AppException(HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(), MALFORMED_CURSOR_MESSAGE);
        }
    }
}
