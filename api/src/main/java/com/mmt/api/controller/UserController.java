//package com.mmt.api.controller;
//
//import com.mmt.api.dto.user.UserCreateRequest;
//import com.mmt.api.dto.user.UserResponse;
//import com.mmt.api.service.UserService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/v1/users")
//public class UserController {
//
//    private final UserService userService;
//
//    public UserController(UserService userService) {
//        this.userService = userService;
//    }
//
//    /**
//     * 유저 회원가입
//     */
//    @PostMapping("")
//    public ResponseEntity<UserResponse> create(@RequestBody UserCreateRequest request) {
//        return ResponseEntity.ok(userService.create(request));
//    }
//
////    /**
////     * 유저 정보 보기
////     */
////    @GetMapping("/")
////    @PreAuthorize("hasAnyRole('USER','ADMIN')")
////    public ResponseEntity<UserResponse> getMyUserInfo(HttpServletRequest request) {
////        return ResponseEntity.ok(userService.getMyUserWithAuthorities());
////    }
////    /**
////     * 유저 정보 보기
////     */
////    @GetMapping("/{userEmail}")
////    @PreAuthorize("hasAnyRole('ADMIN')")
////    public ResponseEntity<UserResponse> getUserInfo(@PathVariable String userEmail) {
////        return ResponseEntity.ok(userService.getUserWithAuthorities(userEmail));
////    }
//
//    /**
//     * 유저 정보 수정
//     */
//    /**
//     * 유저 탈퇴
//     */
//
//
//}
