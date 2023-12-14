package com.mmt.api.controller;

import com.mmt.api.dto.chapter.ChapterResponse;
import com.mmt.api.service.ChapterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chapters")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    /**
     * '학년군&학기(gradeLevel&semester)'에 따른 소단원 목록 보기
     */
    @GetMapping("")
    public List<ChapterResponse> getChapters(@RequestParam("grade") String gradeLevel, @RequestParam("semester") String semester){
        return chapterService.findChapters(gradeLevel, semester);
    }

}
