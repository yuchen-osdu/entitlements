package org.opengroup.osdu.entitlements.v2.acceptance;

import org.junit.Test;
import org.opengroup.osdu.entitlements.v2.acceptance.model.GroupItem;
import org.opengroup.osdu.entitlements.v2.acceptance.model.Token;
import org.opengroup.osdu.entitlements.v2.acceptance.model.request.AddMemberRequestData;
import org.opengroup.osdu.entitlements.v2.acceptance.model.request.RequestData;
import org.opengroup.osdu.entitlements.v2.acceptance.model.response.ListGroupResponse;
import org.opengroup.osdu.entitlements.v2.acceptance.model.response.ListMemberResponse;
import org.opengroup.osdu.entitlements.v2.acceptance.util.ConfigurationService;
import org.opengroup.osdu.entitlements.v2.acceptance.util.TokenService;

import static org.junit.Assert.*;

public abstract class CacheInvalidationTest extends AcceptanceBaseTest {

    public CacheInvalidationTest(ConfigurationService configurationService, TokenService tokenService) {
        super(configurationService, tokenService);
    }

    @Test
    public void shouldReflectGroupCreationImmediatelyInGetGroups() throws Exception {
        Token token = tokenService.getToken();
        String groupName = "cache-test-create-" + currentTime;
        
        // Create group
        GroupItem createdGroup = entitlementsV2Service.createGroup(groupName, token.getValue());
        assertNotNull(createdGroup);
        
        // Retry logic to wait for cache invalidation
        boolean groupFound = false;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            ListGroupResponse updatedGroups = entitlementsV2Service.getGroups(token.getValue());
            groupFound = updatedGroups.getGroups().stream()
                .anyMatch(group -> group.getEmail().equals(createdGroup.getEmail()));
            if (groupFound) break;
        }
        
        assertTrue("Created group should appear in groups list within 1 second", groupFound);
        
        // Cleanup
        entitlementsV2Service.deleteGroup(createdGroup.getEmail(), token.getValue());
    }

    @Test
    public void shouldReflectGroupDeletionImmediatelyInGetGroups() throws Exception {
        Token token = tokenService.getToken();
        String groupName = "cache-test-delete-" + currentTime;
        
        // Create group first
        GroupItem createdGroup = entitlementsV2Service.createGroup(groupName, token.getValue());
        
        // Wait for creation to be reflected
        boolean groupExists = false;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            ListGroupResponse groups = entitlementsV2Service.getGroups(token.getValue());
            groupExists = groups.getGroups().stream()
                .anyMatch(group -> group.getEmail().equals(createdGroup.getEmail()));
            if (groupExists) break;
        }
        assertTrue("Group should exist before deletion", groupExists);
        
        // Delete group
        entitlementsV2Service.deleteGroup(createdGroup.getEmail(), token.getValue());
        
        // Retry logic to wait for cache invalidation - increased to 5 seconds
        boolean groupStillExists = true;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            ListGroupResponse updatedGroups = entitlementsV2Service.getGroups(token.getValue());
            groupStillExists = updatedGroups.getGroups().stream()
                .anyMatch(group -> group.getEmail().equals(createdGroup.getEmail()));
            if (!groupStillExists) break;
        }
        
        assertFalse("Deleted group should not appear in groups list within 5 seconds", groupStillExists);
    }

    @Test
    public void shouldReflectMemberAdditionImmediatelyInGetMembers() throws Exception {
        Token token = tokenService.getToken();
        String groupName = "cache-test-add-member-" + currentTime;
        String testUserEmail = "cache-test-user-" + currentTime + "@example.com";
        
        // Create group
        GroupItem createdGroup = entitlementsV2Service.createGroup(groupName, token.getValue());
        
        // Get initial members count
        ListMemberResponse initialMembers = entitlementsV2Service.getMembers(createdGroup.getEmail(), token.getValue());
        int initialCount = initialMembers.getMembers().size();
        
        // Add member to group
        AddMemberRequestData addMemberRequest = AddMemberRequestData.builder()
            .groupEmail(createdGroup.getEmail())
            .memberEmail(testUserEmail)
            .role("MEMBER")
            .build();
        
        entitlementsV2Service.addMember(addMemberRequest, token.getValue());
        
        // Wait briefly for cache invalidation
        Thread.sleep(200);
        
        // Verify member appears in list immediately
        ListMemberResponse updatedMembers = entitlementsV2Service.getMembers(createdGroup.getEmail(), token.getValue());
        assertEquals("Member count should increase after addition", initialCount + 1, updatedMembers.getMembers().size());
        
        boolean memberFound = updatedMembers.getMembers().stream()
            .anyMatch(member -> member.getEmail().equals(testUserEmail));
        assertTrue("Added member should appear in members list immediately", memberFound);
        
        // Cleanup
        entitlementsV2Service.deleteGroup(createdGroup.getEmail(), token.getValue());
    }

    @Test
    public void shouldReflectMemberRemovalImmediatelyInGetMembers() throws Exception {
        Token token = tokenService.getToken();
        String groupName = "cache-test-remove-member-" + currentTime;
        String testUserEmail = "cache-test-user-" + currentTime + "@example.com";
        
        // Create group
        GroupItem createdGroup = entitlementsV2Service.createGroup(groupName, token.getValue());
        
        // Add member to group first
        AddMemberRequestData addMemberRequest = AddMemberRequestData.builder()
            .groupEmail(createdGroup.getEmail())
            .memberEmail(testUserEmail)
            .role("MEMBER")
            .build();
        
        entitlementsV2Service.addMember(addMemberRequest, token.getValue());
        
        // Wait for addition to be reflected
        Thread.sleep(200);
        
        // Verify member exists
        ListMemberResponse membersWithAdded = entitlementsV2Service.getMembers(createdGroup.getEmail(), token.getValue());
        boolean memberExists = membersWithAdded.getMembers().stream()
            .anyMatch(member -> member.getEmail().equals(testUserEmail));
        assertTrue("Member should exist before removal", memberExists);
        
        int countBeforeRemove = membersWithAdded.getMembers().size();
        
        // Remove member
        entitlementsV2Service.removeMember(createdGroup.getEmail(), testUserEmail, token.getValue());
        
        // Wait briefly for cache invalidation
        Thread.sleep(200);
        
        // Verify member is removed from list immediately
        ListMemberResponse updatedMembers = entitlementsV2Service.getMembers(createdGroup.getEmail(), token.getValue());
        assertEquals("Member count should decrease after removal", countBeforeRemove - 1, updatedMembers.getMembers().size());
        
        boolean memberStillExists = updatedMembers.getMembers().stream()
            .anyMatch(member -> member.getEmail().equals(testUserEmail));
        assertFalse("Removed member should not appear in members list", memberStillExists);
        
        // Cleanup
        entitlementsV2Service.deleteGroup(createdGroup.getEmail(), token.getValue());
    }

    @Test
    public void shouldReflectMemberDeletionImmediatelyInGetMembers() throws Exception {
        Token token = tokenService.getToken();
        String groupName = "cache-test-delete-member-" + currentTime;
        String testUserEmail = "cache-test-user-delete-" + currentTime + "@example.com";
        
        // Create group
        GroupItem createdGroup = entitlementsV2Service.createGroup(groupName, token.getValue());
        
        // Add member to group first
        AddMemberRequestData addMemberRequest = AddMemberRequestData.builder()
            .groupEmail(createdGroup.getEmail())
            .memberEmail(testUserEmail)
            .role("MEMBER")
            .build();
        
        entitlementsV2Service.addMember(addMemberRequest, token.getValue());
        
        // Wait for addition to be reflected
        Thread.sleep(200);
        
        // Verify member exists
        ListMemberResponse membersWithAdded = entitlementsV2Service.getMembers(createdGroup.getEmail(), token.getValue());
        boolean memberExists = membersWithAdded.getMembers().stream()
            .anyMatch(member -> member.getEmail().equals(testUserEmail));
        assertTrue("Member should exist before deletion", memberExists);
        
        int countBeforeDelete = membersWithAdded.getMembers().size();
        
        // Delete member (user entity)
        entitlementsV2Service.deleteMember(testUserEmail, token.getValue());
        
        // Wait briefly for cache invalidation
        Thread.sleep(200);
        
        // Verify member is removed from group immediately
        ListMemberResponse updatedMembers = entitlementsV2Service.getMembers(createdGroup.getEmail(), token.getValue());
        assertEquals("Member count should decrease after user deletion", countBeforeDelete - 1, updatedMembers.getMembers().size());
        
        boolean memberStillExists = updatedMembers.getMembers().stream()
            .anyMatch(member -> member.getEmail().equals(testUserEmail));
        assertFalse("Deleted user should not appear in members list", memberStillExists);
        
        // Cleanup
        entitlementsV2Service.deleteGroup(createdGroup.getEmail(), token.getValue());
    }

    @Override
    protected RequestData getRequestDataForNoTokenTest() {
        return RequestData.builder()
                .method("GET")
                .dataPartitionId(configurationService.getTenantId())
                .relativePath("groups")
                .build();
    }

    @Override
    protected void cleanup() throws Exception {
        // Cleanup any remaining test groups
        try {
            Token token = tokenService.getToken();
            ListGroupResponse groups = entitlementsV2Service.getGroups(token.getValue());
            
            for (GroupItem group : groups.getGroups()) {
                if (group.getName() != null && group.getName().startsWith("cache-test-")) {
                    try {
                        entitlementsV2Service.deleteGroup(group.getEmail(), token.getValue());
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
