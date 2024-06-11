package com.mmt.api.service.user;

import com.mmt.api.domain.user.Authority;
import com.mmt.api.domain.user.UserAuthority;
import com.mmt.api.domain.user.Users;
import com.mmt.api.dto.user.UserDTO;
import com.mmt.api.exception.DuplicateMemberException;
import com.mmt.api.exception.NotFoundMemberException;
import com.mmt.api.repository.users.UserAuthorityRepository;
import com.mmt.api.repository.users.UsersRepository;
import com.mmt.api.util.SecurityUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class UserService {

    private final UsersRepository usersRepository;
    private final UserAuthorityRepository userAuthorityRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UsersRepository usersRepository, UserAuthorityRepository userAuthorityRepository, PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.userAuthorityRepository = userAuthorityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * signup : 회원가입
     */
    @Transactional
    public UserDTO signup(UserDTO userDTO) {
        if (usersRepository.findOneWithAuthoritiesByUserEmail(userDTO.getUserEmail()).orElse(null) != null) {
            throw new DuplicateMemberException("이미 가입되어 있는 유저입니다.");
        }

        Authority authority = Authority.builder()
                .authorityName("ROLE_USER")
                .build();
        UserAuthority userAuthority = UserAuthority.builder()
                .authority(authority)
                .build();
        Users user = Users.builder()
                .userEmail(userDTO.getUserEmail())
                .userPassword(passwordEncoder.encode(userDTO.getUserPassword()))
                .userName(userDTO.getUserName())
                .userPhone(userDTO.getUserPhone())
                .userBirthdate(userDTO.getUserBirthdate())
                .userComments(userDTO.getUserComments())
                .userAuthoritySet(Collections.singleton(userAuthority))
                .activated(true)
                .build();
        Users saveUser = usersRepository.save(user);

        // user_authority 테이블에 user_id 갱신
        user.setUserId(saveUser.getUserId());
        userAuthority.setUser(user);
        userAuthorityRepository.save(userAuthority);

        return UserDTO.from(saveUser);
    }

    /**
     * getUserWithAuthorities : userEmail에 따른 유저 정보, 권한 정보를 가져온다.
     */
    @Transactional(readOnly = true)
    public UserDTO getUserWithAuthorities(String userEmail) {
        return UserDTO.from(usersRepository.findOneWithAuthoritiesByUserEmail(userEmail).orElse(null));
    }

    /**
     * getMyUserWithAuthorities : 현재 Security Context에 저장되어 있는 userEmail에 따른 유저 정보, 권한 정보를 가져온다.
     */
    @Transactional(readOnly = true)
    public UserDTO getMyUserWithAuthorities() {
        return UserDTO.from(
                SecurityUtil.getCurrentUserEmail()
                        .flatMap(usersRepository::findOneWithAuthoritiesByUserEmail)
                        .orElseThrow(() -> new NotFoundMemberException("Member not found"))
        );
    }

    /**
     * getMyUserIdWithAuthorities : 현재 Security Context에 저장되어 있는 userEmail에 따른 userId를 가져온다.
     */
    @Transactional(readOnly = true)
    public Long getMyUserIdWithAuthorities() {
        return SecurityUtil.getCurrentUserEmail()
                        .flatMap(usersRepository::findUserIdByUserEmail)
                        .orElseThrow(() -> new NotFoundMemberException("Member not found"));
    }


    /**
     * 회원 정보 수정
     */
    @Transactional
    public boolean updateUser(UserDTO userDTO) {
        try {
            // 현재 로그인된 사용자의 이메일 가져오기
            String loggedInUserEmail = SecurityUtil.getCurrentUserEmail()
                    .orElseThrow(() -> new RuntimeException("현재 사용자의 이메일을 가져올 수 없습니다."));

            // 기존 사용자 정보 가져오기
            Users existingUser = usersRepository.findOneWithAuthoritiesByUserEmail(loggedInUserEmail)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

            // 필요한 정보 업데이트
            if (userDTO.getUserPassword() != null && !userDTO.getUserPassword().isEmpty()) existingUser.setUserPassword(passwordEncoder.encode(userDTO.getUserPassword()));
            if (userDTO.getUserName() != null && !userDTO.getUserName().isEmpty()) existingUser.setUserName(userDTO.getUserName());
            if (userDTO.getUserBirthdate() != null ) existingUser.setUserBirthdate(userDTO.getUserBirthdate());
            if (userDTO.getUserComments() != null && !userDTO.getUserComments().isEmpty()) existingUser.setUserComments(userDTO.getUserComments());

            // 하나라도 필드가 엠티가 아닌 경우에만 사용자 정보 저장
            if (!userDTO.getUserPassword().isEmpty() ||
                    !userDTO.getUserName().isEmpty() ||
                    userDTO.getUserBirthdate() !=null ||
                    !userDTO.getUserComments().isEmpty()) {
                // 사용자 정보 저장
                Users updatedUser = usersRepository.save(existingUser);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Transactional
    public void delete(Long userId) {
        if(!usersRepository.existsByUserId(userId)){
            throw new IllegalArgumentException();
        }
        usersRepository.deleteById(userId);
    }

    @Transactional
    public boolean isDuplicateUser(String userEmail) {
        return usersRepository.findOneWithAuthoritiesByUserEmail(userEmail).isPresent();
    }

}
