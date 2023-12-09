// 모든 노드 보기
MATCH (n) RETURN (n);

// 모든 노드와 관계 삭제
MATCH (a) OPTIONAL MATCH (a)-[r]-() DELETE a, r;
MATCH (a:concept) OPTIONAL MATCH (a)-[r]-() DELETE a, r;

// 경로 길이 1~6 인 노드와 관계
MATCH path = (start_node)-[*1..6]->(n {concept_id: 4979})
RETURN nodes(path), relationships(path);

// 경로 길이 1~6 인 노드
MATCH path = (start_node)-[*1..6]->(n {concept_id: 4979})
RETURN nodes(path);
// 경로 길이 1~6 인 노드 찾는 다른 방법? - 위의 쿼리문과 차이점 찾아보기
MATCH path = (n)-[*1..6]->(m {concept_id: $conceptId}) RETURN (n);

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
// path 없이도 id 나오는 것 가능 (중복제거 몇 개 빼고 됨)

// node를 key:value = concept_id:skill_id 로 추출


// 같은 소단원(name)인 노드들 (대단원은 main, 중단원은 sub)
MATCH (n {concept_id: 4015}), (m)
WHERE m.chapter_name = n.chapter_name
RETURN (m);

