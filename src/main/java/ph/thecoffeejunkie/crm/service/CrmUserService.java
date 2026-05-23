package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrmUserService {

    private final CRMUserRepository crmUserRepository;

    public List<CRMUser> findAll() {
        return crmUserRepository.findAll();
    }

}
