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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.entitlements.v2.jdbc.model.MemberInfoEntity;

class MemberInfoEntityNullRoleMapperTest {

    private final MemberInfoEntityNullRoleMapper sut = new MemberInfoEntityNullRoleMapper();

    @Test
    void mapRow_populatesIdEmailAndPartition_leavesRoleNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(7L);
        when(rs.getString("email")).thenReturn("user@example.com");
        when(rs.getString("partition_id")).thenReturn("dp");

        MemberInfoEntity entity = sut.mapRow(rs, 0);

        assertEquals(7L, entity.getId());
        assertEquals("user@example.com", entity.getEmail());
        assertEquals("dp", entity.getPartitionId());
        // Mapper name promises no role — must be null even if column exists.
        assertNull(entity.getRole());
    }

    @Test
    void mapRow_sqlException_isPropagated() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("email")).thenThrow(new SQLException("boom"));

        assertThrows(SQLException.class, () -> sut.mapRow(rs, 0));
    }
}
