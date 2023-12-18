select * from chapters;

select * from users;

SELECT chapter_id, chapter_name, chapter_main, chapter_sub FROM chapters WHERE grade_level = "초1" AND semester = "1학기";


-- 비어있는 건 없음. 모두 빈 문자열
select * from chapters where chapter_main = "";
select * from chapters where chapter_main is null;

select * from chapters where chapter_sub = "다항식의 인수분해";
select * from chapters where chapter_main = "다항식의 곱셈과 인수분해";

SELECT concept_id, concept_name, concept_description, concept_achievement_name FROM concepts WHERE concept_chapter_id=343;
