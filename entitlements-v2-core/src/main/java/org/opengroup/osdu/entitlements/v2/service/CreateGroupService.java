//  Copyright © Microsoft Corporation
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.opengroup.osdu.entitlements.v2.service;

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.entitlements.v2.logging.AuditLogger;
import org.opengroup.osdu.entitlements.v2.model.creategroup.CreateGroupRepoDto;
import org.opengroup.osdu.entitlements.v2.model.creategroup.CreateGroupServiceDto;
import org.opengroup.osdu.entitlements.v2.model.EntityNode;
import org.opengroup.osdu.entitlements.v2.model.ParentReference;
import org.opengroup.osdu.entitlements.v2.service.featureflag.FeatureFlag;
import org.opengroup.osdu.entitlements.v2.service.featureflag.PartitionFeatureFlagService;
import org.opengroup.osdu.entitlements.v2.spi.creategroup.CreateGroupRepo;
import org.opengroup.osdu.entitlements.v2.spi.retrievegroup.RetrieveGroupRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CreateGroupService {

    @Value("${app.quota.users.data.root:5000}")
    private int dataRootGroupQuota;

    private final CreateGroupRepo createGroupRepo;
    private final RetrieveGroupRepo retrieveGroupRepo;
    private final GroupCacheService groupCacheService;
    private final JaxRsDpsLog log;
    private final DefaultGroupsService defaultGroupsService;
    private final PartitionFeatureFlagService partitionFeatureFlagService;
    private final AuditLogger auditLogger;

    public EntityNode run(EntityNode groupNode, CreateGroupServiceDto createGroupServiceDto) {
        String groupNodeId = groupNode.getNodeId();
        String partitionId = createGroupServiceDto.getPartitionId();
        String requesterId = createGroupServiceDto.getRequesterId();

        log.debug(String.format("requested by %s", requesterId));
        EntityNode requesterNode = EntityNode.createMemberNodeForRequester(requesterId, partitionId);
        Set<ParentReference> allExistingParentsOfRequester = groupCacheService.getFromPartitionCache(requesterId, partitionId);

        boolean isGroupParentOfRequester = allExistingParentsOfRequester.stream().anyMatch(parent -> groupNodeId.equalsIgnoreCase(parent.getId()));

        if (isGroupParentOfRequester) {
            throw new AppException(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "This group already exists");
        }

        this.validateGroupMembershipLimit(requesterId, allExistingParentsOfRequester.size(), EntityNode.MAX_PARENTS);

        EntityNode dataRootGroupNode = null;
        boolean addDataRootGroup = false;

        if (this.shouldAddDataRootGroupInTheHierarchy(partitionId, groupNode)) {
            dataRootGroupNode = retrieveGroupRepo.groupExistenceValidation(String.format(EntityNode.ROOT_DATA_GROUP_EMAIL_FORMAT, createGroupServiceDto.getPartitionDomain()), partitionId);
            addDataRootGroup = true;

            Set<ParentReference> allExistingParentsOfRootDataGroup = retrieveGroupRepo.loadAllParents(dataRootGroupNode).getParentReferences();

            this.validateGroupMembershipLimit(dataRootGroupNode.getNodeId(), allExistingParentsOfRootDataGroup.size(), dataRootGroupQuota);

            log.debug(String.format("Creating a group with root group node: %s", dataRootGroupNode.getName()));
        } else {
            log.debug("Creating a group with no root group node");
        }

        CreateGroupRepoDto createGroupRepoDto = CreateGroupRepoDto.builder()
                .requesterNode(requesterNode)
                .dataRootGroupNode(dataRootGroupNode)
                .addDataRootGroup(addDataRootGroup)
                .partitionId(partitionId)
                .build();

        createGroup(groupNode, createGroupRepoDto);

        return groupNode;
    }

    private boolean shouldAddDataRootGroupInTheHierarchy(String dataPartitionId, EntityNode groupNode) {
        return !this.partitionFeatureFlagService.getFeature(FeatureFlag.DISABLE_DATA_ROOT_GROUP_HIERARCHY.label, dataPartitionId)
                && groupNode.isDataGroup() && defaultGroupsService.isNotDefaultGroupName(groupNode.getName());
    }

    private void validateGroupMembershipLimit(String memberId, int groupMembershipCount, int groupMembershipLimit) {
        if (groupMembershipCount >= groupMembershipLimit) {
            log.error(String.format("Identity %s already belongs to %d groups", memberId, groupMembershipCount));
            throw new AppException(
                HttpStatus.PRECONDITION_FAILED.value(),
                HttpStatus.PRECONDITION_FAILED.getReasonPhrase(),
                String.format("%s's group quota hit. Identity can't belong to more than %d groups", memberId, groupMembershipLimit));
        }
    }

    private void createGroup(EntityNode groupNode, CreateGroupRepoDto createGroupRepoDto) {
        try {
            Set<String> impactedUsers = createGroupRepo.createGroup(groupNode, createGroupRepoDto);
            groupCacheService.refreshListGroupCache(impactedUsers, createGroupRepoDto.getPartitionId());
            auditLogger.createGroupSuccess(groupNode.getNodeId());
        } catch (Exception e) {
            auditLogger.createGroupFailure(groupNode.getNodeId());
            throw e;
        }
    }
}