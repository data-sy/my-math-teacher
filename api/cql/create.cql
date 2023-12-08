// 모든 노드와 관계 삭제
MATCH (a) OPTIONAL MATCH (a)-[r]-() DELETE a, r
MATCH (a:concept) OPTIONAL MATCH (a)-[r]-() DELETE a, r

// csv 파일 import 후

// concepts 전체
LOAD CSV WITH HEADERS FROM "file:///concepts.csv" AS row
CREATE (:concept {name: row.name, concept_id: toInteger(row.id), desc: row.description, section : row.section, school_level:row.school_level, grade_level:row.grade_level, semester:row.semester, chapter_id:toInteger(row.chapter_id), chapter_main: row.chapter_main, chapter_sub: row.chapter_sub, chapter_name: row.chapter_name, achievement_id:toInteger(row.achievement_id), achievement_name:row.achievement_name, skill_id:toInteger(row.skill_id)});

// knowledge_space
LOAD CSV WITH HEADERS FROM "file:///knowledge_space.csv" AS row
MATCH (a:concept {concept_id: toInteger(row.to_concept_id)}), (b:concept {concept_id: toInteger(row.from_concept_id)})
CREATE (a)-[r:KNOWLEDGE_SPACE {knowledge_space_id: toInteger(row.id) }]->(b)
