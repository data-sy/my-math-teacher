//package com.mmt.api.service;
//
//import com.mmt.api.domain.user.Authority;
//import com.mmt.api.domain.user.UserAuthority;
//import com.mmt.api.domain.user.Users;
//import com.mmt.api.dto.user.UserConverter;
//import com.mmt.api.dto.user.UserCreateRequest;
//import com.mmt.api.dto.user.UserResponse;
//import com.mmt.api.exception.DuplicateMemberException;
//import com.mmt.api.repository.Users.UserAuthorityRepository;
//import com.mmt.api.repository.Users.UsersRepository;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.Collections;
//
//@Service
//public class UserService {
//    private final UsersRepository usersRepository;
//    private final UserAuthorityRepository userAuthorityRepository;
//
//// security 추가하면
//    //    private final PasswordEncoder passwordEncoder;
//
//    public UserService(UsersRepository usersRepository, UserAuthorityRepository userAuthorityRepository) {
//        this.usersRepository = usersRepository;
//        this.userAuthorityRepository = userAuthorityRepository;
//    }
//
//    /**
//     * signup : 회원가입
//     */
//    @Transactional
//    public UserResponse create(UserCreateRequest request) {
//        if (usersRepository.findOneWithAuthoritiesByUserEmail(request.getUserEmail()).orElse(null) != null) {
//            throw new DuplicateMemberException("이미 가입되어 있는 유저입니다.");
//        }
//
//        Authority authority = Authority.builder()
//                .authorityName("ROLE_USER")
//                .build();
//        UserAuthority userAuthority = UserAuthority.builder()
//                .authority(authority)
//                .build();
//        Users user = Users.builder()
//                .userEmail(request.getUserEmail())
//                .userPassword(request.getUserPassword())
////                .userPassword(passwordEncoder.encode(userDTO.getUserPassword()))
//                .userName(request.getUserName())
//                .userPhone(request.getUserPhone())
//                .userBirthdate(request.getUserBirthdate())
//                .userComments(request.getUserComments())
//                .userAuthoritySet(Collections.singleton(userAuthority))
//                .activated(true)
//                .build();
//        Users saveUser = usersRepository.save(user);
//
//        // user_authority 테이블에 user_id 갱신
//        user.setUserId(saveUser.getUserId());
//        userAuthority.setUser(user);
//        userAuthorityRepository.save(userAuthority);
//
//        return UserConverter.convertToUserResponse(saveUser);
//    }
//
//    /**
//     * getUserWithAuthorities : userEmail에 따른 유저 정보, 권한 정보를 가져온다.
//     */
//    @Transactional(readOnly = true)
//    public UserResponse getUserWithAuthorities(String userEmail) {
//        return UserConverter.convertToUserResponse(usersRepository.findOneWithAuthoritiesByUserEmail(userEmail).orElse(null));
//    }
//
////    /**
////     * getUserWithAuthorities : 현재 Security Context에 저장되어 있는 userEmail에 따른 유저 정보, 권한 정보를 가져온다.
////     */
////    @Transactional(readOnly = true)
////    public UserDTO getMyUserWithAuthorities() {
////        return UserDTO.from(
////                SecurityUtil.getCurrentUserEmail()
////                        .flatMap(usersRepository::findOneWithAuthoritiesByUserEmail)
////                        .orElseThrow(() -> new NotFoundMemberException("Member not found"))
////        );
////    }
//
//}
