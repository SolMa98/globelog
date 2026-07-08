package kr.co.dh.globelog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryImageRepository extends JpaRepository<CountryImage, Long> {

    List<CountryImage> findByCountryOrderBySortOrderAsc(Country country);

    List<CountryImage> findByCountryIdOrderBySortOrderAsc(Long countryId);
}
