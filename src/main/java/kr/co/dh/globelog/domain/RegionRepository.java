package kr.co.dh.globelog.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    List<Region> findByCountryIdOrderByNameKoAsc(Long countryId);

    List<Region> findByCountryIdAndEnabledTrueOrderByNameKoAsc(Long countryId);

    Page<Region> findByCountryId(Long countryId, Pageable pageable);
}