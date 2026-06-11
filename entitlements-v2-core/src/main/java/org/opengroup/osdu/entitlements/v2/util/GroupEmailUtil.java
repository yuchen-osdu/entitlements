/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.entitlements.v2.util;

/**
 * Utility class for group email validation and identification.
 */
public final class GroupEmailUtil {

    private GroupEmailUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if the given email follows the group email pattern: ends with @{partition}.{domain}
     *
     * @param email the email to check
     * @param partitionId the partition ID
     * @param domain the domain
     * @return true if the email is a group email, false otherwise
     */
    public static boolean isGroupEmail(String email, String partitionId, String domain) {
        if (email == null || partitionId == null || domain == null) {
            return false;
        }
        return email.endsWith(String.format("@%s.%s", partitionId, domain));
    }
}
