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

package org.opengroup.osdu.entitlements.v2.jdbc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntityList;

class GroupInfoEntityListMapperTest {

    private ObjectMapper objectMapper;
    private GroupInfoEntityListMapper sut;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sut = new GroupInfoEntityListMapper(objectMapper);
    }

    @Test
    void mapRow_populatesEntitiesAndTotalCount_fromJsonPayload() throws SQLException {
        String json = "[{\"id\":1,\"name\":\"users.x\",\"email\":\"users.x@dp.group.com\","
                + "\"description\":\"desc\",\"partitionId\":\"dp\"}]";
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("groupInfoEntities")).thenReturn(json);
        when(rs.getLong("totalCount")).thenReturn(42L);

        GroupInfoEntityList result = sut.mapRow(rs, 0);

        assertNotNull(result);
        assertEquals(42L, result.getTotalCount());
        assertNotNull(result.getGroupInfoEntities());
        assertEquals(1, result.getGroupInfoEntities().size());
        assertEquals("users.x", result.getGroupInfoEntities().get(0).getName());
        assertEquals("users.x@dp.group.com", result.getGroupInfoEntities().get(0).getEmail());
    }

    @Test
    void mapRow_nullJsonPayload_returnsEmptyEntityListWithTotalCount() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("groupInfoEntities")).thenReturn(null);
        when(rs.getLong("totalCount")).thenReturn(0L);

        GroupInfoEntityList result = sut.mapRow(rs, 0);

        assertNotNull(result);
        assertEquals(0L, result.getTotalCount());
        assertTrue(result.getGroupInfoEntities().isEmpty());
    }

    @Test
    void mapRow_emptyStringJsonPayload_returnsEmptyEntityListWithTotalCount() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("groupInfoEntities")).thenReturn("");
        when(rs.getLong("totalCount")).thenReturn(7L);

        GroupInfoEntityList result = sut.mapRow(rs, 0);

        assertEquals(7L, result.getTotalCount());
        assertTrue(result.getGroupInfoEntities().isEmpty());
    }

    @Test
    void mapRow_malformedJson_throwsAppExceptionWithInternalServerErrorStatus() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        // Non-parseable JSON causes JsonProcessingException, which the mapper wraps as AppException.
        when(rs.getString("groupInfoEntities")).thenReturn("this is not json");

        AppException ex = assertThrows(AppException.class, () -> sut.mapRow(rs, 0));
        assertEquals(500, ex.getError().getCode());
    }

    @Test
    void mapRow_sqlException_throwsAppExceptionWithInternalServerErrorStatus() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        // ResultSet failure at the JDBC layer is remapped to a 500 AppException.
        when(rs.getString("groupInfoEntities")).thenThrow(new SQLException("connection lost"));

        AppException ex = assertThrows(AppException.class, () -> sut.mapRow(rs, 0));
        assertEquals(500, ex.getError().getCode());
    }
}
