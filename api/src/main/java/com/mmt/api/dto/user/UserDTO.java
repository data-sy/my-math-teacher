package com.mmt.api.dto.user;

import com.mmt.api.domain.user.Authority;
import com.mmt.api.domain.user.UserAuthority;
import com.mmt.api.domain.user.Users;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    private Long userId;

    private String userEmail;

    //    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String userPassword;

    private String userName;

    private String userPhone;

    private LocalDate userBirthdate;

    private String userComments;

    private Set<Authority> authoritySet;

    public static UserDTO from(Users user) {
        if(user == null) return null;

        return UserDTO.builder()
                .userId(user.getUserId())
                .userEmail(user.getUserEmail())
                .userName(user.getUserName())
                .userPhone(user.getUserPhone())
                .userBirthdate(user.getUserBirthdate())
                .userComments(user.getUserComments())
                .authoritySet(user.getUserAuthoritySet().stream()
                        .map(userAuthority -> Authority.builder().authorityName(userAuthority.getAuthority().getAuthorityName()).build())
                        .collect(Collectors.toSet()))
                .build();
    }

    public static Users toEntity(UserDTO userDTO) {
        if (userDTO == null) return null;

        return Users.builder()
                .userId(userDTO.getUserId())
                .userEmail(userDTO.getUserEmail())
                .userName(userDTO.getUserName())
                .userPhone(userDTO.getUserPhone())
                .userBirthdate(userDTO.getUserBirthdate())
                .userComments(userDTO.getUserComments())
                .userAuthoritySet(userDTO.getAuthoritySet().stream()
                        .map(authority -> UserAuthority.builder()
                                .authority(Authority.builder().authorityName(authority.getAuthorityName()).build())
                                .build())
                        .collect(Collectors.toSet()))
                .build();
    }

}