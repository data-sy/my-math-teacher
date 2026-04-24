package com.mmt.api.repository.users;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.domain.user.Authority;
import com.mmt.api.domain.user.UserAuthority;
import com.mmt.api.domain.user.Users;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Users / UserAuthority / Authority 엔티티의 fetch 전략:
// - Users.userAuthoritySet: @OneToMany (default LAZY)
// - UserAuthority.user: @ManyToOne(fetch = EAGER)
// - UserAuthority.authority: @ManyToOne (default EAGER)
//
// `findOneWithAuthoritiesByUserEmail` 은 @EntityGraph("userAuthoritySet") 로
// Users + UserAuthority 를 join fetch. Authority 는 포함 안 되므로 별도 쿼리.
// M1 은 현 상태 baseline 캡처가 목적이므로 EntityGraph 를 더 확장하지 않음.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
class UsersRepositoryN1Test {

    @Autowired
    private UsersRepository usersRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics stats() {
        return entityManager
            .unwrap(Session.class)
            .getSessionFactory()
            .getStatistics();
    }

    private Users newUser(String email) {
        return Users.builder()
            .userEmail(email)
            .userPassword("pw")
            .userName("u-" + email)
            .userPhone("010")
            .userBirthdate(LocalDate.of(2000, 1, 1))
            .userComments("")
            .activated(true)
            .build();
    }

    @BeforeEach
    void resetStats() {
        stats().setStatisticsEnabled(true);
        stats().clear();
    }

    @Test
    void findOneWithAuthoritiesFetchesInBoundedQueries() {
        // 시드: Authority 1, Users 1, UserAuthority 1
        Authority auth = Authority.builder().authorityName("ROLE_USER").build();
        entityManager.persist(auth);

        Users user = newUser("one@example.com");
        entityManager.persist(user);

        UserAuthority ua = UserAuthority.builder().user(user).authority(auth).build();
        entityManager.persist(ua);
        // @Builder 가 userAuthoritySet 필드 초기화(new HashSet<>())를 bypass 하므로
        // 인메모리 컬렉션 갱신은 생략. flush+clear 후 DB 에서 재로딩되면서 채워짐.

        entityManager.flush();
        entityManager.clear();
        stats().clear();

        Optional<Users> loaded = usersRepository.findOneWithAuthoritiesByUserEmail("one@example.com");
        // userAuthoritySet + 각 ua 의 authority 까지 접근해 lazy 여지 전부 소진
        loaded.get().getUserAuthoritySet().forEach(UserAuthority::getAuthority);

        long queryCount = stats().getPrepareStatementCount();
        // 기대 쿼리:
        //   1) Users + UserAuthority join (@EntityGraph 효과)
        //   2) Authority (UserAuthority.authority EAGER ManyToOne 로 인한 추가 조회)
        // 현 엔티티 구조에서 ==1 은 불가. M2 관련 리팩토링이 아니므로 상한 기준만 검증.
        assertThat(queryCount).isLessThanOrEqualTo(3);
    }

    @Test
    void findAllThenAccessAuthoritiesDemonstratesN1Baseline() {
        // 시드: 공통 Authority 1, Users 3, 각 Users 당 UserAuthority 1
        Authority auth = Authority.builder().authorityName("ROLE_USER").build();
        entityManager.persist(auth);

        for (String email : List.of("a@e.com", "b@e.com", "c@e.com")) {
            Users user = newUser(email);
            entityManager.persist(user);
            UserAuthority ua = UserAuthority.builder().user(user).authority(auth).build();
            entityManager.persist(ua);
        }

        entityManager.flush();
        entityManager.clear();
        stats().clear();

        List<Users> allUsers = usersRepository.findAll();
        // LAZY 로딩 트리거: 각 user 의 userAuthoritySet 접근
        allUsers.forEach(u -> u.getUserAuthoritySet().size());

        long queryCount = stats().getPrepareStatementCount();
        // findAll 1회 + N users × (userAuthoritySet 조회) = N+1.
        // N=3 기준 최소 4 이상이어야 anti-pattern 이 재현된 것.
        // 이 테스트가 통과한다는 것은 현 구조에 N+1 이 실제 존재함을 의미 (baseline 기록).
        assertThat(queryCount).isGreaterThanOrEqualTo(4);
    }
}
