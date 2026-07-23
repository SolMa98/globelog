package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.Region;
import kr.co.dh.globelog.domain.RegionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CountrySeeder와 동일한 "없는 것만 추가, 있는 건 안 건드림(idempotent)" 계약을
 * Region에서도 지키는지 검증한다. 특히 국가별로 이미 등록된 지역 이름은 건너뛰어야
 * 하고, provinces.json에 있어도 country 테이블에 매칭되는 국가가 없으면 무시해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class RegionSeederTest {

    @Mock
    private RegionRepository regionRepository;
    @Mock
    private CountryRepository countryRepository;
    @Mock
    private ProvinceRefService provinceRefService;

    @Test
    void 없는_지역만_새로_등록한다() {
        Country korea = new Country("KOR", "KR", "대한민국", "South Korea", null);
        when(countryRepository.findByIsoA3("KOR")).thenReturn(Optional.of(korea));
        when(provinceRefService.getAll()).thenReturn(Map.of("KOR", List.of(
                new ProvinceRefEntry("서울", "Seoul", 37.5, 127.0),
                new ProvinceRefEntry("부산", "Busan", 35.1, 129.0))));
        when(regionRepository.findByCountryIdOrderByNameKoAsc(any())).thenReturn(List.of());

        RegionSeeder seeder = new RegionSeeder(regionRepository, countryRepository, provinceRefService);
        seeder.run(null);

        ArgumentCaptor<List<Region>> captor = ArgumentCaptor.forClass(List.class);
        verify(regionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().stream().map(Region::getNameKo)).containsExactlyInAnyOrder("서울", "부산");
    }

    @Test
    void 이미_등록된_이름은_다시_추가하지_않는다() {
        Country korea = new Country("KOR", "KR", "대한민국", "South Korea", null);
        when(countryRepository.findByIsoA3("KOR")).thenReturn(Optional.of(korea));
        when(provinceRefService.getAll()).thenReturn(Map.of("KOR", List.of(
                new ProvinceRefEntry("서울", "Seoul", 37.5, 127.0),
                new ProvinceRefEntry("부산", "Busan", 35.1, 129.0))));
        Region existingSeoul = new Region(korea, "서울", "Seoul", null, 37.5, 127.0);
        when(regionRepository.findByCountryIdOrderByNameKoAsc(any())).thenReturn(List.of(existingSeoul));

        RegionSeeder seeder = new RegionSeeder(regionRepository, countryRepository, provinceRefService);
        seeder.run(null);

        ArgumentCaptor<List<Region>> captor = ArgumentCaptor.forClass(List.class);
        verify(regionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getNameKo()).isEqualTo("부산");
    }

    @Test
    void country_테이블에_없는_국가코드는_건너뛴다() {
        when(countryRepository.findByIsoA3("XXX")).thenReturn(Optional.empty());
        when(provinceRefService.getAll()).thenReturn(Map.of("XXX", List.of(
                new ProvinceRefEntry("어딘가", "Somewhere", 0.0, 0.0))));

        RegionSeeder seeder = new RegionSeeder(regionRepository, countryRepository, provinceRefService);
        seeder.run(null);

        verify(regionRepository, never()).saveAll(any());
    }
}
