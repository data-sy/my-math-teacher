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


    // 비밀번호 암호화를 적용하지 않아서 문자열 그대로 save 됨
    // 암호화 시도했었는데 같은 문자열이어도 암호화 결과가 바뀌어버려.
    // 이 부분은 더 공부해서 나중에 기능 사용하게 될 때 수정하자.
//    public void update(UserDTO userDTO) {
//        if(!usersRepository.existsByUserId(userDTO.getUserId())){
//            throw new IllegalArgumentException();
//        }
//        usersRepository.save(userDTO.toEntity(userDTO));
//    }

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
