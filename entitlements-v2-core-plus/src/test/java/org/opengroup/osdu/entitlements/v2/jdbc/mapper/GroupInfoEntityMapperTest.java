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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntity;

class GroupInfoEntityMapperTest {

    private final GroupInfoEntityMapper sut = new GroupInfoEntityMapper();

    @Test
    void mapRow_populatesAllColumns_andLowercasesName() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(101L);
        // Intentionally mixed-case: mapper must normalize to lowercase for consistent lookups.
        when(rs.getString("name")).thenReturn("Users.Data.Root");
        when(rs.getString("description")).thenReturn("Data root group");
        when(rs.getString("email")).thenReturn("users.data.root@dp.group.com");
        when(rs.getString("partition_id")).thenReturn("dp");

        GroupInfoEntity entity = sut.mapRow(rs, 0);

        assertEquals(101L, entity.getId());
        assertEquals("users.data.root", entity.getName());
        assertEquals("Data root group", entity.getDescription());
        assertEquals("users.data.root@dp.group.com", entity.getEmail());
        assertEquals("dp", entity.getPartitionId());
    }

    @Test
    void mapRow_alreadyLowercaseName_isReturnedAsIs() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("name")).thenReturn("users.a");
        when(rs.getString("description")).thenReturn(null);
        when(rs.getString("email")).thenReturn("users.a@dp.group.com");
        when(rs.getString("partition_id")).thenReturn("dp");

        GroupInfoEntity entity = sut.mapRow(rs, 0);

        assertEquals("users.a", entity.getName());
    }

    @Test
    void mapRow_sqlExceptionOnName_propagates() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        // Underlying ResultSet failure must not be swallowed — surefire needs the SQLException.
        when(rs.getString("name")).thenThrow(new SQLException("boom"));

        assertThrows(SQLException.class, () -> sut.mapRow(rs, 0));
    }
}
