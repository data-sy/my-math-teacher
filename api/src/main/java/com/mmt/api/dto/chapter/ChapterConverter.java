package com.mmt.api.dto.chapter;

import com.mmt.api.domain.Chapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChapterConverter {

    public static List<ChapterResponse> convertListToChapterResponseList(List<Chapter> chapters){
        int mainKeyCnt = 0;
        int subKeyCnt = 0;
        Map<String, ChapterResponse> chapterMainMap = new HashMap<>();
        Map<String, ChapterResponse> chapterSubMap = new HashMap<>();

        // 대단원
        for (Chapter chapter : chapters){
            if(!chapterMainMap.containsKey(chapter.getChapterMain())){
                ChapterResponse mainChapter = new ChapterResponse();
                mainChapter.setKey(Integer.toString(mainKeyCnt++));
                mainChapter.setLabel(chapter.getChapterMain());
                mainChapter.setIcon("pi pi-fw pi-inbox");
                chapterMainMap.put(chapter.getChapterMain(), mainChapter);
            }
        }
        // 중단원
        for (Chapter chapter : chapters) {
            ChapterResponse mainChapter = chapterMainMap.get(chapter.getChapterMain());
            if (mainChapter != null) {
                if (!chapterSubMap.containsKey(chapter.getChapterSub())) {
                    ChapterResponse subChapter = new ChapterResponse();
                    subChapter.setKey(mainChapter.getKey() + '-' + Integer.toString(subKeyCnt++));
                    subChapter.setLabel(chapter.getChapterSub());
                    subChapter.setIcon("pi pi-fw pi-folder");
                    chapterSubMap.put(chapter.getChapterSub(), subChapter);
                    mainChapter.getChildren().add(subChapter);
                }
            }
        }
        // 소단원
        for (Chapter chapter : chapters) {
            ChapterResponse subChapter = chapterSubMap.get(chapter.getChapterSub());
            if (subChapter != null) {
                ChapterResponse chapterName = new ChapterResponse();
                chapterName.setKey(Integer.toString(chapter.getChapterId()));
                chapterName.setLabel(chapter.getChapterName());
                chapterName.setIcon("pi pi-fw pi-bars");
                subChapter.getChildren().add(chapterName);
            }
        }

        List<ChapterResponse> chapterResponseList = new ArrayList<>(chapterMainMap.values());

        return chapterResponseList;
    }
}
