package kr.co.dh.globelog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminIpWhitelistEntryRepository extends JpaRepository<AdminIpWhitelistEntry, Long> {

    List<AdminIpWhitelistEntry> findAllByOrderByCreatedAtDesc();

    boolean existsByCidr(String cidr);
}
