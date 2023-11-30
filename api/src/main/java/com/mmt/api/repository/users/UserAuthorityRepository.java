package com.mmt.api.repository.users;

import com.mmt.api.domain.user.UserAuthority;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthorityRepository extends JpaRepository<UserAuthority, Long> {

}
