package kr.co.dh.globelog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, Long> {

    Optional<Country> findByIsoA3(String isoA3);

    boolean existsByIsoA3(String isoA3);

    List<Country> findByEnabledTrueOrderByNameKoAsc();

    Page<Country> findByNameKoContainingIgnoreCaseOrNameEnContainingIgnoreCase(
            String nameKo, String nameEn, Pageable pageable);
}
