package com.mmt.api.repository.chapter;

import com.mmt.api.domain.Chapter;

import java.util.List;

public interface ChapterRepository {

    List<Chapter> findAllByGradeLevelAndSemester(String gradeLevel, String semester);

}
