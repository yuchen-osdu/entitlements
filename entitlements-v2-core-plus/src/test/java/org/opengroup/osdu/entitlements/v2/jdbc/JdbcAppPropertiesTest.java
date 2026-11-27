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

package org.opengroup.osdu.entitlements.v2.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.entitlements.v2.model.init.AliasEntity;
import org.opengroup.osdu.entitlements.v2.model.init.InitServiceDto;

class JdbcAppPropertiesTest {

    private JdbcAppProperties sut;

    @BeforeEach
    void setUp() {
        sut = new JdbcAppProperties();
    }

    @Test
    void getInitialGroups_returnsThreeExpectedProvisioningFiles() {
        List<String> initialGroups = sut.getInitialGroups();

        assertNotNull(initialGroups);
        assertEquals(3, initialGroups.size());
        // Order matters — bootstrap consumers rely on the sequence.
        assertEquals("/provisioning/groups/datalake_user_groups.json", initialGroups.get(0));
        assertEquals("/provisioning/groups/datalake_service_groups.json", initialGroups.get(1));
        assertEquals("/provisioning/groups/data_groups.json", initialGroups.get(2));
    }

    @Test
    void getGroupsOfServicePrincipal_returnsCanonicalPath() {
        assertEquals("/provisioning/accounts/groups_of_service_principal.json",
                sut.getGroupsOfServicePrincipal());
    }

    @Test
    void getGroupsOfInitialUsers_whenInitServiceDtoIsNull_returnsOnlyServicePrincipalPath() {
        // Default (null) initServiceDto — path list must not be populated with alias entries.
        List<String> groups = sut.getGroupsOfInitialUsers();
        assertEquals(1, groups.size());
        assertEquals("/provisioning/accounts/groups_of_service_principal.json", groups.get(0));
    }

    @Test
    void getGroupsOfInitialUsers_whenInitServiceDtoHasEmptyAliasList_returnsOnlyServicePrincipalPath() {
        InitServiceDto dto = InitServiceDto.builder()
                .aliasMappings(Collections.emptyList())
                .build();
        sut.setInitServiceDto(dto);

        List<String> groups = sut.getGroupsOfInitialUsers();
        assertEquals(1, groups.size());
        assertEquals("/provisioning/accounts/groups_of_service_principal.json", groups.get(0));
    }

    @Test
    void getGroupsOfInitialUsers_whenAliasesPresent_returnsServicePrincipalPathPlusEachAliasLowercased() {
        // The alias id must be lowercased regardless of input case.
        AliasEntity a1 = AliasEntity.builder().userId("u1").aliasId("Workflow").build();
        AliasEntity a2 = AliasEntity.builder().userId("u2").aliasId("SEISMIC").build();
        InitServiceDto dto = InitServiceDto.builder()
                .aliasMappings(Arrays.asList(a1, a2))
                .build();
        sut.setInitServiceDto(dto);

        List<String> groups = sut.getGroupsOfInitialUsers();

        assertEquals(3, groups.size());
        assertEquals("/provisioning/accounts/groups_of_service_principal.json", groups.get(0));
        assertEquals("/provisioning/accounts/groups_of_workflow.json", groups.get(1));
        assertEquals("/provisioning/accounts/groups_of_seismic.json", groups.get(2));
    }

    @Test
    void getProtectedMembers_returnsExpectedFiles() {
        List<String> protectedMembers = sut.getProtectedMembers();

        assertEquals(2, protectedMembers.size());
        assertTrue(protectedMembers.contains("/provisioning/groups/data_groups.json"));
        assertTrue(protectedMembers.contains("/provisioning/groups/datalake_service_groups.json"));
    }
}
