package com.equisplit.repository;

import com.equisplit.entity.GroupMember;
import com.equisplit.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    Optional<GroupMember> findByIdGroupIdAndIdUserId(UUID groupId, UUID userId);

    boolean existsByIdGroupIdAndIdUserId(UUID groupId, UUID userId);

    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.user WHERE gm.id.groupId = :groupId ORDER BY gm.joinedAt ASC")
    List<GroupMember> findMembersByGroupIdWithUser(@Param("groupId") UUID groupId);

    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.group g WHERE gm.id.userId = :userId ORDER BY g.createdAt DESC")
    List<GroupMember> findUserMembershipsWithGroup(@Param("userId") UUID userId);

    List<GroupMember> findByIdGroupId(UUID groupId);

    List<GroupMember> findByIdUserId(UUID userId);

    long countByIdGroupIdAndRole(UUID groupId, String role);

    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.role = 'admin' AND gm.id.userId <> :userId")
    long countOtherAdmins(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT gm.id.userId FROM GroupMember gm WHERE gm.id.groupId = :groupId AND gm.id.userId IN :userIds")
    List<UUID> findExistingMemberUserIds(@Param("groupId") UUID groupId, @Param("userIds") List<UUID> userIds);
}
