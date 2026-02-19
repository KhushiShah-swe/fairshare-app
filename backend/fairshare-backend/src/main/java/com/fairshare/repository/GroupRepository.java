package com.fairshare.repository;

import com.fairshare.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Finds a group by its unique invite code.
     * Standard search (Case-Sensitive).
     */
    Optional<Group> findByInviteCode(String inviteCode);

    /**
     * Finds a group by invite code ignoring case.
     * Highly recommended for 6-character alphanumeric codes to improve user experience.
     * Derived Query: SELECT * FROM app_groups WHERE LOWER(invite_code) = LOWER(?)
     */
    Optional<Group> findByInviteCodeIgnoreCase(String inviteCode);

    /**
     * Note: JpaRepository already provides:
     * - findById(Long id)
     * - save(Group group)
     * - saveAndFlush(Group group) 
     * - deleteById(Long id)
     */
}