package com.mmt.api.dto.user;

import com.mmt.api.domain.user.Authority;
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

}