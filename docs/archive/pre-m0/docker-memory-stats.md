# Docker Container Memory Usage (2026-03-31)

## Container Memory Stats

| Service | Container Name | MEM USAGE | MEM % | CPU % | PIDS |
|---------|---------------|-----------|-------|-------|------|
| MySQL | my-math-teacher-mmt-mysql-1 | 356.3 MiB | 4.55% | 2.31% | 49 |
| Neo4j | my-math-teacher-mmt-neo4j-1 | 306.1 MiB | 3.91% | 1.73% | 65 |
| Backend (Spring Boot) | ec2-user-mmt-backend-1 | 293.0 MiB | 3.74% | 0.29% | 46 |
| AI (TensorFlow Serving) | my-math-teacher-mmt-ai-1 | 34.57 MiB | 0.44% | 0.09% | 51 |
| Redis | my-math-teacher-mmt-redis-1 | 11.03 MiB | 0.14% | 0.32% | 6 |
| Frontend (Nginx) | my-math-teacher-mmt-front-1 | 6.38 MiB | 0.08% | 0.00% | 9 |

- Host Memory Limit: **7.653 GiB**

---

## Neo4j Memory Analysis

### Neo4j가 전체에서 차지하는 비율

$$
\text{Total} = 356.3 + 306.1 + 293.0 + 34.57 + 11.03 + 6.38 = 1007.38 \text{ MiB}
$$

$$
\text{Neo4j 비율} = \frac{306.1}{1007.38} \times 100 \approx 30.38\%
$$

### Neo4j 제거 시 메모리 효율 개선

$$
\text{Neo4j 제거 후 Total} = 1007.38 - 306.1 = 701.28 \text{ MiB}
$$

$$
\text{메모리 절감률} = \frac{306.1}{1007.38} \times 100 \approx 30.38\%
$$

$$
\text{잔여 서비스 기준 여유 메모리 증가율} = \frac{306.1}{701.28} \times 100 \approx 43.65\%
$$

### 결론

- Neo4j는 전체 컨테이너 메모리의 약 **30.38%** 를 차지한다.
- Neo4j를 제거하면 전체 메모리 사용량이 **1007.38 MiB -> 701.28 MiB**로 감소한다.
- 나머지 서비스 입장에서는 사용 가능한 여유 메모리가 약 **43.65%** 증가한다.
