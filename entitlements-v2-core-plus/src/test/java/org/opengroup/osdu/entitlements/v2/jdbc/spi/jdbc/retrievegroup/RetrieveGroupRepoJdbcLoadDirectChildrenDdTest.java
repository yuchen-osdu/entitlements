/*
 *  Copyright 2024 Google LLC
 *  Copyright 2024 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.retrievegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opengroup.osdu.entitlements.v2.jdbc.JdbcAppProperties;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.DbTestConfig;
import org.opengroup.osdu.entitlements.v2.jdbc.spi.jdbc.repository.JdbcTemplateRunner;
import org.opengroup.osdu.entitlements.v2.model.ChildrenReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Database-driven integration test for {@link RetrieveGroupRepoJdbc#loadDirectChildren}.
 *
 * <p>Unlike {@code RetrieveGroupRepoJdbcTest} (which mocks the repositories), this test wires the
 * real {@code GroupRepository} and {@code MemberRepository} against an embedded H2 database loaded
 * from {@code /sql/retrieveGroupRepoJdbcTestData.sql}. It exercises the actual JDBC queries that
 * back {@code loadDirectChildren} and asserts the combined child-groups + members result end to end
 * at the repository + DB level.
 *
 * <p>The seeded graph is:
 * <pre>
 *         /- group3
 *   group1 - group2 - group4
 *   |                 |
 *   user1(OWNER)      user1(MEMBER)
 * </pre>
 *
 * <p>This directly guards the regression fixed in MR !950: {@code loadDirectChildren} previously
 * built its child-group list with {@code Stream.toList()} (immutable) and then called
 * {@code addAll(members)}, throwing {@link UnsupportedOperationException} for every group that had
 * both child groups and direct members. {@code group1} is exactly that case.
 */
@SpringBootTest(classes = DbTestConfig.class)
@AutoConfigureTestDatabase
@Sql(value = {"/sql/schema.sql", "/sql/retrieveGroupRepoJdbcTestData.sql"},
    executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/drop_schema.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
@ExtendWith(SpringExtension.class)
class RetrieveGroupRepoJdbcLoadDirectChildrenDdTest {

  private static final String PARTITION_ID = "dp";
  private static final String GROUP_1 = "group1@dp.group.com";
  private static final String GROUP_2 = "group2@dp.group.com";
  private static final String GROUP_3 = "group3@dp.group.com";
  private static final String GROUP_4 = "group4@dp.group.com";
  private static final String USER_1 = "user1@xxx.com";

  // loadDirectChildren does not touch these collaborators; mock them so the context matches the
  // proven RetrieveGroupRepoJdbcDdTest wiring while GroupRepository/MemberRepository stay real.
  @MockBean
  private JdbcAppProperties jdbcAppProperties;
  @MockBean
  private JdbcTemplateRunner jdbcTemplateRunner;

  @Autowired
  private RetrieveGroupRepoJdbc sut;

  @Test
  void shouldReturnChildGroupsAndMembersWhenGroupHasBoth() {
    // group1 has direct child groups group2 + group3 and a direct member user1(OWNER).
    // This is the combined path that previously threw UnsupportedOperationException.
    List<ChildrenReference> children = sut.loadDirectChildren(PARTITION_ID, GROUP_1);

    assertEquals(3, children.size());

    Map<String, ChildrenReference> byId = children.stream()
        .collect(Collectors.toMap(ChildrenReference::getId, Function.identity()));

    assertTrue(byId.containsKey(GROUP_2));
    assertTrue(byId.containsKey(GROUP_3));
    assertTrue(byId.containsKey(USER_1));

    assertTrue(byId.get(GROUP_2).isGroup());
    assertTrue(byId.get(GROUP_3).isGroup());
    assertTrue(!byId.get(USER_1).isGroup());
  }

  @Test
  void shouldReturnOnlyChildGroupWhenGroupHasNoDirectMembers() {
    // group2 has a single child group (group4) and no direct members.
    List<ChildrenReference> children = sut.loadDirectChildren(PARTITION_ID, GROUP_2);

    assertEquals(1, children.size());
    assertEquals(GROUP_4, children.get(0).getId());
    assertTrue(children.get(0).isGroup());
  }

  @Test
  void shouldReturnOnlyMembersWhenGroupHasNoChildGroups() {
    // group4 has a direct member (user1) but no child groups.
    List<ChildrenReference> children = sut.loadDirectChildren(PARTITION_ID, GROUP_4);

    assertEquals(1, children.size());
    assertEquals(USER_1, children.get(0).getId());
    assertTrue(!children.get(0).isGroup());
  }

  @Test
  void shouldReturnEmptyWhenGroupHasNoChildren() {
    // group3 is a leaf: no child groups, no members.
    List<ChildrenReference> children = sut.loadDirectChildren(PARTITION_ID, GROUP_3);

    assertTrue(children.isEmpty());
  }
}
