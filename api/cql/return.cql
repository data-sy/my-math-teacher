// 모든 노드 보기
MATCH (n) RETURN (n);
MATCH (n{concept_id: 4979}) RETURN (n);

// 모든 노드와 관계 삭제
MATCH (a) OPTIONAL MATCH (a)-[r]-() DELETE a, r;
MATCH (a:concept) OPTIONAL MATCH (a)-[r]-() DELETE a, r;
// 특정 관계 삭제
MATCH ()-[r:관계 이름]-() DELETE r

// 경로 길이 1~6 인 노드와 관계
MATCH path = (start_node)-[*1..6]->(n {concept_id: 4979})
RETURN nodes(path), relationships(path);
// 위 쿼리의 경로 개수
MATCH path = (start_node)-[*1..6]->(n {concept_id: 4979})
RETURN COUNT(path) AS path_count;

// 경로 길이 1~6 인 노드
MATCH path = (start_node)-[*1..6]->(n {concept_id: 4979})
RETURN nodes(path);
// 경로 길이 1~6 인 노드 찾는 다른 방법 (위 쿼리문과 차이점 찾아보기 : 결과는 같은데 체감상 얘가 빨라)
MATCH path = (n)-[*1..6]->(m {concept_id: 4979}) RETURN (n);

// 경로 길이 3인 노드들의 concept_id들
// 이 때, n은 여러 번 반복되서 나올 수 있음 (보니까, id를 dfs처럼 생성. n에서 뻗어나온 관계 개수만큼 나오는 듯?)
// collect()로 받는 건 java에서 어떤 데이터로 받아야 할지 모르겠음
//MATCH path = (start_node)-[*3]->(n {concept_id: 4979})
//WITH nodes(path) AS connected_nodes
//UNWIND connected_nodes AS node
//RETURN collect(node.concept_id) AS concept_ids
// 그래서 Flux로 받을 수 있도록 수정
MATCH path = (start_node)-[*1..3]->(n {concept_id: 4979})
WITH nodes(path) AS connected_nodes
UNWIND connected_nodes AS node
RETURN [id IN node.concept_id] AS concept_ids
// path 없이도 id 나오는 것 가능 (여러 경로 있는 id 빼고 중복제거 됨) 하지만 id는 경로가 중요해서 이 쿼리는 쓸 일이 없네
//MATCH (n)-[*1..3]->(m {concept_id: 4979})
//RETURN [id IN n.concept_id]


// node를 key:value = concept_id:skill_id 로 추출


// 같은 소단원(name)인 노드들 (대단원은 main, 중단원은 sub)
MATCH (n {concept_id: 4015}), (m)
WHERE m.chapter_name = n.chapter_name
RETURN (m);

