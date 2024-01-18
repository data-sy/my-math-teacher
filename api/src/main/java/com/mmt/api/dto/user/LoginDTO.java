package com.mmt.api.dto.user;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginDTO {

    private String userEmail;
    private String userPassword;

}
