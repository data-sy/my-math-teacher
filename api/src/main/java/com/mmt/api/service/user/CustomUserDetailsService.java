package com.mmt.api.service.user;

import com.mmt.api.domain.user.Users;
import com.mmt.api.repository.users.UsersRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component("userDetailsService")
public class CustomUserDetailsService implements UserDetailsService {

    private final UsersRepository usersRepository;

    public CustomUserDetailsService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    /**
     * 로그인 시 DB에서 유저정보와 권한정보를 가져온다.
     * */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(final String userEmail) {
        return usersRepository.findOneWithAuthoritiesByUserEmail(userEmail)
                .map(user -> createUser(userEmail, user))
                .orElseThrow(() -> new UsernameNotFoundException(userEmail + " -> 데이터베이스에서 찾을 수 없습니다."));
    }

    private org.springframework.security.core.userdetails.User createUser(String userEmail, Users user) {
        if (!user.isActivated()) {
            throw new RuntimeException(userEmail + " -> 활성화되어 있지 않습니다.");
        }

        List<GrantedAuthority> grantedAuthorities = user.getUserAuthoritySet().stream()
                .map(authority -> new SimpleGrantedAuthority(authority.getAuthority().getAuthorityName()))
                .collect(Collectors.toList());

        return new org.springframework.security.core.userdetails.User(user.getUserEmail(),
                user.getUserPassword(),
                grantedAuthorities);
    }

}
