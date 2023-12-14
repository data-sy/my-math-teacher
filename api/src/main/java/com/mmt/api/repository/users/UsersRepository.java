package com.mmt.api.repository.users;

import com.mmt.api.domain.user.Users;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {
    @EntityGraph(attributePaths = "userAuthoritySet")
    Optional<Users> findOneWithAuthoritiesByUserEmail(String userEmail);

    @Query("SELECT u.userId FROM Users u WHERE u.userEmail = :userEmail")
    Optional<Long> findUserIdByUserEmail(@Param("userEmail") String userEmail);

    boolean existsByUserId(Long userId);

}