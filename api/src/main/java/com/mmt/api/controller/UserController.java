package com.mmt.api.controller;

import com.mmt.api.dto.user.UserDTO;
import com.mmt.api.service.user.AuthService;
import com.mmt.api.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /**
     * 상세보기 (자기 자신의 정보)
     */
    @GetMapping("")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserDTO> getUserMyInfo() {
        return ResponseEntity.ok(userService.getMyUserWithAuthorities());
    }

    /**
     * ADMIN 권한의 특정 유저 상세보기
     */
    @GetMapping("/{userEmail}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<UserDTO> getUserInfo(@PathVariable String userEmail) {
        return ResponseEntity.ok(userService.getUserWithAuthorities(userEmail));
    }

    /**
     * 수정하기
     */
    @PutMapping("")
    public boolean updateUser(@RequestBody UserDTO userDTO) {
        return userService.updateUser(userDTO);
    }

    /**
     * 탈퇴하기
     */
    @DeleteMapping("")
    public ResponseEntity<Void> delete(HttpServletRequest request) {
        Long userId = userService.getMyUserIdWithAuthorities();
        // 로그아웃
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring(7);
            authService.logout(accessToken);
        }
        userService.delete(userId);
        return ResponseEntity.ok().build();
    }

}
