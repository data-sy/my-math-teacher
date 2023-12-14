package com.mmt.api.service;

import com.mmt.api.dto.chapter.ChapterConverter;
import com.mmt.api.dto.chapter.ChapterResponse;
import com.mmt.api.repository.chapter.ChapterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;

    public ChapterService(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    public List<ChapterResponse> findChapters(String gradeLevel, String semester){
        return ChapterConverter.convertListToChapterResponseList(chapterRepository.findAllByGradeLevelAndSemester(gradeLevel, semester));
    }
}
