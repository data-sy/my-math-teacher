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

// 경로 길이 3인 노드들의 concept_id들
// 이 때, n은 여러 번 반복되서 나올 수 있음 (보니까, id를 dfs처럼 생성. n에서 뻗어나온 관계 개수만큼 나오는 듯?)
MATCH path = (start_node)-[*3]->(n {concept_id: 4979})
WITH nodes(path) AS connected_nodes
UNWIND connected_nodes AS node
RETURN collect(node.concept_id) AS concept_ids

// 같은 소단원(name)인 노드들 (대단원은 main, 중단원은 sub)
MATCH (n {concept_id: 4015}), (m)
WHERE m.chapter_name = n.chapter_name
RETURN (m);
