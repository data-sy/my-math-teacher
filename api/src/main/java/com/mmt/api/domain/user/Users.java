package com.mmt.api.domain.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mmt.api.oauth2.AuthProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Users {

    @JsonIgnore
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private String userEmail;

    @JsonIgnore
    private String userPassword;

    private String userName;

    private String userPhone;

    private LocalDate userBirthdate;

    private String userComments;

    // 활셩화 여부
    @JsonIgnore
    private boolean activated;

    // OAuth로 추가된 컬럼
    @JsonIgnore
    private String oauth2Id;

    @JsonIgnore
    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider; // GOOGLE, NAVER, KAKAO

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserAuthority> userAuthoritySet = new HashSet<>();

}
