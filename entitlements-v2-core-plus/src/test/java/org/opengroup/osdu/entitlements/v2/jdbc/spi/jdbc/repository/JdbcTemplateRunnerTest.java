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

package org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.entitlements.v2.jdbc.mapper.GroupInfoEntityListMapper;
import org.opengroup.osdu.entitlements.v2.jdbc.model.GroupInfoEntityList;
import org.opengroup.osdu.entitlements.v2.jdbc.model.MemberInfoEntity;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.GroupType;
import org.opengroup.osdu.entitlements.v2.model.NodeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

@ExtendWith(MockitoExtension.class)
class JdbcTemplateRunnerTest {

    private static final String PARTITION_ID = "dp";
    private static final String GROUP_EMAIL = "users.data.root@dp.group.com";

    @Mock
    private GroupInfoEntityListMapper groupInfoEntityListMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private JdbcTemplateRunner sut;

    @Test
    void saveMemberInfoEntity_insertsMemberAndReturnsGeneratedId() throws SQLException {
        MemberInfoEntity member = MemberInfoEntity.builder()
                .email("user@example.com")
                .partitionId(PARTITION_ID)
                .role("MEMBER")
                .build();

        // Simulate JdbcTemplate.update populating the KeyHolder like Postgres RETURNING id would.
        doAnswer(inv -> {
            KeyHolder kh = inv.getArgument(1);
            // KeyHolder.getKey() is expected to be a Number. Provide via generatedKeys list.
            kh.getKeyList().add(Collections.singletonMap("id", 42L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        // Prove the SQL is prepared with the email + partition_id in that order.
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString(), eq(java.sql.Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);

        ArgumentCaptor<PreparedStatementCreator> psCap =
                ArgumentCaptor.forClass(PreparedStatementCreator.class);
        Long id = sut.saveMemberInfoEntity(member);

        verify(jdbcTemplate).update(psCap.capture(), any(KeyHolder.class));
        // Invoke the captured PreparedStatementCreator to verify the SQL and parameter binding.
        psCap.getValue().createPreparedStatement(conn);
        verify(conn).prepareStatement(
                "INSERT INTO member(email, partition_id) VALUES (?, ?) RETURNING id",
                java.sql.Statement.RETURN_GENERATED_KEYS);
        verify(ps).setString(1, "user@example.com");
        verify(ps).setString(2, PARTITION_ID);

        assertEquals(42L, id);
    }

    @Test
    void getGroupsInPartition_addsAllNamedParameters_andReturnsMapperResult() {
        GroupInfoEntityList expected = GroupInfoEntityList.builder()
                .totalCount(5L)
                .groupInfoEntities(Collections.emptyList())
                .build();
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class),
                eq(groupInfoEntityListMapper))).thenReturn(expected);

        GroupInfoEntityList actual = sut.getGroupsInPartition(PARTITION_ID, GroupType.USER, 10, 20);

        assertEquals(5L, actual.getTotalCount());

        // Verify the name_prefix / partition / limit / offset params were all passed through.
        ArgumentCaptor<MapSqlParameterSource> paramsCap =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate)
                .queryForObject(sqlCap.capture(), paramsCap.capture(), eq(groupInfoEntityListMapper));

        MapSqlParameterSource params = paramsCap.getValue();
        assertEquals(PARTITION_ID, params.getValue("partition"));
        assertEquals("user%", params.getValue("name_prefix"));
        assertEquals(20, params.getValue("limit"));
        assertEquals(10, params.getValue("from_row"));

        // Non-NONE groupType branch must include the name_prefix filter fragment.
        assertTrue(sqlCap.getValue().contains("name LIKE :name_prefix"),
                "expected name_prefix filter for non-NONE group type");
    }

    @Test
    void getGroupsInPartition_groupTypeNone_omitsNameLikeFilter() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class),
                eq(groupInfoEntityListMapper))).thenReturn(
                GroupInfoEntityList.builder().totalCount(0L)
                        .groupInfoEntities(Collections.emptyList()).build());

        sut.getGroupsInPartition(PARTITION_ID, GroupType.NONE, 0, 50);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).queryForObject(sqlCap.capture(),
                any(MapSqlParameterSource.class), eq(groupInfoEntityListMapper));
        // GroupType.NONE takes the false branch, so the LIKE filter must be absent.
        assertFalse(sqlCap.getValue().contains("name LIKE :name_prefix"),
                "GroupType.NONE should omit name LIKE filter");
        // ...but the base partition predicate must always be present regardless of group type.
        assertTrue(sqlCap.getValue().contains("partition_id = :partition"),
                "base partition_id = :partition predicate must be preserved");
    }

    @Test
    void getAffectedMembersForGroup_bindsPartitionAndGroupEmail_returnsSetFromQuery() {
        EntityNode group = EntityNode.builder()
                .nodeId(GROUP_EMAIL)
                .type(NodeType.GROUP)
                .name("users.data.root")
                .dataPartitionId(PARTITION_ID)
                .build();

        List<String> emails = Arrays.asList("a@example.com", "b@example.com", "a@example.com");
        when(namedParameterJdbcTemplate.queryForList(anyString(),
                any(MapSqlParameterSource.class), eq(String.class))).thenReturn(emails);

        Set<String> result = sut.getAffectedMembersForGroup(group);

        // Set deduplicates the list of 3 -> 2 distinct emails.
        assertEquals(2, result.size());
        assertTrue(result.contains("a@example.com"));
        assertTrue(result.contains("b@example.com"));

        ArgumentCaptor<MapSqlParameterSource> paramsCap =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedParameterJdbcTemplate).queryForList(anyString(), paramsCap.capture(), eq(String.class));
        assertEquals(PARTITION_ID, paramsCap.getValue().getValue("partition"));
        assertEquals(GROUP_EMAIL, paramsCap.getValue().getValue("group_email"));
    }

    @Test
    void getAffectedMembersForGroup_emptyResult_returnsEmptySet() {
        EntityNode group = EntityNode.builder()
                .nodeId(GROUP_EMAIL)
                .type(NodeType.GROUP)
                .name("users.data.root")
                .dataPartitionId(PARTITION_ID)
                .build();
        when(namedParameterJdbcTemplate.queryForList(anyString(),
                any(MapSqlParameterSource.class), eq(String.class))).thenReturn(Collections.emptyList());

        Set<String> result = sut.getAffectedMembersForGroup(group);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
