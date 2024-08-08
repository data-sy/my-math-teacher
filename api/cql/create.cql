// 모든 노드와 관계 삭제
MATCH (a) OPTIONAL MATCH (a)-[r]-() DELETE a, r
MATCH (a:concept) OPTIONAL MATCH (a)-[r]-() DELETE a, r

// csv 파일 import 후
// concepts
LOAD CSV WITH HEADERS FROM "file:///concepts.csv" AS row
CREATE (:concept {name: row.name, concept_id: toInteger(row.id), desc: row.description, section : row.section, school_level:row.school_level, grade_level:row.grade_level, semester:row.semester, chapter_id:toInteger(row.chapter_id), chapter_main: row.chapter_main, chapter_sub: row.chapter_sub, chapter_name: row.chapter_name, achievement_id:toInteger(row.achievement_id), achievement_name:row.achievement_name, skill_id:toInteger(row.skill_id)});
// knowledge_space
LOAD CSV WITH HEADERS FROM "file:///knowledge_space.csv" AS row
MATCH (a:concept {concept_id: toInteger(row.to_concept_id)}), (b:concept {concept_id: toInteger(row.from_concept_id)})
CREATE (a)-[r:KNOWLEDGE_SPACE {knowledge_space_id: toInteger(row.id) }]->(b);

// 인덱스
// 인덱스 생성
CREATE INDEX FOR (c:concept) ON (c.concept_id); // 이름 없으면 show로 찾아서 지워야 함
CREATE INDEX chapter_id_index FOR (n:concept) ON (n.chapter_id);
// 인덱스 목록
SHOW INDEXES;
// 목록에서 이름 찾아서 인덱스 삭제
DROP INDEX index_7837418c;
DROP INDEX chapter_id_index;

// 쿼리 실행 계획 확인
PROFILE MATCH (n {chapter_id: 500}) RETURN (n);
PROFILE MATCH (n:concept {chapter_id: 500}) RETURN (n);
PROFILE MATCH (n:concept {chapter_id: 500}) WITH n RETURN n;


// 데이터 개수 늘리기
// 노드 이름, 관계 이름 변화
// 노드
LOAD CSV WITH HEADERS FROM "file:///concepts.csv" AS row
CREATE (:concept02 {name: row.name, concept_id: toInteger(row.id), desc: row.description, section : row.section, school_level:row.school_level, grade_level:row.grade_level, semester:row.semester, chapter_id:toInteger(row.chapter_id), chapter_main: row.chapter_main, chapter_sub: row.chapter_sub, chapter_name: row.chapter_name, achievement_id:toInteger(row.achievement_id), achievement_name:row.achievement_name, skill_id:toInteger(row.skill_id)});
// 관계
LOAD CSV WITH HEADERS FROM "file:///knowledge_space.csv" AS row
MATCH (a:concept {concept_id: toInteger(row.to_concept_id)}), (b:concept {concept_id: toInteger(row.from_concept_id)})
CREATE (a)-[r:KNOWLEDGE_SPACE02 {knowledge_space_id: toInteger(row.id) }]->(b);
// 데이터 수정
MATCH (a:concept03) SET a.chapter_id = a.chapter_id * 1000 (680번정도 까지 있어서)

