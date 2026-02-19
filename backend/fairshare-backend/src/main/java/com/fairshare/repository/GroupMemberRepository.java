package com.fairshare.repository;

import com.fairshare.model.GroupMember;
import com.fairshare.model.User;
import com.fairshare.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    
    /**
     * Used by GroupService.getUserGroups(userId) 
     */
    List<GroupMember> findByUserId(Long userId);
    
    /**
     * Fetches all members of a specific group.
     */
    List<GroupMember> findByGroupId(Long groupId);

    /**
     * Optimized check for membership.
     * Used in JoinGroup logic to prevent duplicate entries.
     */
    boolean existsByUserIdAndGroupId(Long userId, Long groupId);

    /**
     * Helpful for specific permission checks (e.g., is this user an ADMIN?).
     * Returning Optional allows for clean handling in GroupService.
     */
    Optional<GroupMember> findByUserIdAndGroupId(Long userId, Long groupId);

    /**
     * BULK DELETE: Removes all members associated with a group.
     * Required for the 'Delete Group' feature.
     * @Modifying is required for DELETE/UPDATE queries.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM GroupMember gm WHERE gm.group.id = :groupId")
    void deleteByGroupId(Long groupId);

    /**
     * Find by User object - useful for internal service logic.
     */
    List<GroupMember> findByUser(User user);
}