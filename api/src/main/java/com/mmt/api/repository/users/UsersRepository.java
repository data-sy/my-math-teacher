package com.mmt.api.repository.users;

import com.mmt.api.domain.user.Users;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {
    @EntityGraph(attributePaths = "userAuthoritySet")
    Optional<Users> findOneWithAuthoritiesByUserEmail(String UserEmail);

}