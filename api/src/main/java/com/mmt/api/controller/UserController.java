package com.mmt.api.controller;

import com.mmt.api.dto.user.UserDTO;
import com.mmt.api.service.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 유저 상세보기 (자기 자신의 정보)
     */
    @GetMapping("")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserDTO> getMyUserInfo() {
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
     * 유저 수정하기
     */
//    @PutMapping("")
//    public void update(@RequestBody UserDTO userDTO) {
//        userService.update(userDTO);
//    }

    /**
     * 유저 탈퇴하기
     */
    @DeleteMapping("")
    public void delete() {
        Long userId = userService.getMyUserIdWithAuthorities();
        userService.delete(userId);
    }

//    // 현재 Security Context에 따른 userId 가져오기 테스트
//    @GetMapping("/id")
//    public ResponseEntity<Long> getMyUserId() {
//        return ResponseEntity.ok(userService.getMyUserIdWithAuthorities());
//    }

}
