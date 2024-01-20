package com.mmt.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmt.api.domain.user.Authority;
import com.mmt.api.domain.user.UserAuthority;
import com.mmt.api.domain.user.Users;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "아이디를 입력하세요.")
    @Pattern(regexp = "^[a-z0-9_-]{5,20}$", message = "아이디는 5~20자의 영문 소문자, 숫자만 사용 가능합니다.")
    private String userEmail;

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,16}$", message = "비밀번호는 8~16자, 최소한 하나의 대문자, 하나의 소문자, 하나의 숫자를 포함해야 합니다.")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String userPassword;

    @Size(max = 20, message = "이름은 20자 이하로 가능합니다.")
    private String userName;

    @Size(max = 20, message = "전화번호는 20자 이하로 가능합니다.")
    private String userPhone;

    private LocalDate userBirthdate;

    @Size(max = 200, message = "기타사항은 200자 이하로 가능합니다.")
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