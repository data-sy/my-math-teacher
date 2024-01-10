package com.mmt.api.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/v1/hello")
public class HelloController {

    @GetMapping("")
    public ResponseEntity<String> hello(){
        return ResponseEntity.ok("hello");
    }

    @PostMapping("")
    public ResponseEntity<String> helloPost(){
        return ResponseEntity.ok("hello post!!");
    }

    @GetMapping("/image")
    public ResponseEntity<String> imageTest(){
        return ResponseEntity.ok("<a href=\"https://ibb.co/WD0RmTC\"><img src=\"https://i.ibb.co/KND8T1n/dog.jpg\" alt=\"dog\" border=\"0\" /></a>");
    }

//    @GetMapping("/gogo")
//    public ResponseEntity<String> corsTest(@RequestParam("data") String data){
//        // 이 코드에서는 들어온 data를 새로운 엔드포인트로 전달하는 방법을 보여줍니다.
//
//        // 바디에 들어갈 데이터 설정
//        String requestBody = data;
//        System.out.println(data);
//
//        // 새로운 엔드포인트 URL
//        String newEndpointUrl = "http://localhost:5173/#/gogo";
//
//        // RestTemplate 생성
//        RestTemplate restTemplate = new RestTemplate();
//
//        // HTTP 요청 헤더 설정
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON); // 예시로 JSON 형식을 사용
//
//        // HTTP 요청 생성
//        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
//
//        // 새로운 엔드포인트에 POST 요청 보내기
//        ResponseEntity<String> response = restTemplate.exchange(newEndpointUrl, HttpMethod.GET, request, String.class);
//
//        // 새로운 엔드포인트에서 받은 응답(response) 처리
//        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
//        if (statusCode == HttpStatus.OK) {
//            String responseBody = response.getBody();
//            // 받은 응답에 따른 추가 로직 수행
//            // responseBody를 확인하고 원하는 처리를 수행합니다.
//            // 예를 들어, 받은 responseBody를 클라이언트에 다시 전달하거나 다른 로직을 수행할 수 있습니다.
//            return new ResponseEntity<>(responseBody, HttpStatus.OK);
//        } else {
//            // 적절한 에러 처리 로직을 수행합니다.
//            return new ResponseEntity<>("Error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }
}
