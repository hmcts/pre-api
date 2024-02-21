package uk.gov.hmcts.reform.preapi.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.RoleDTO;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public List<RoleDTO> getAllRoles() {
        return roleRepository
            .findAll()
            .stream()
            .map(RoleDTO::new)
            .collect(Collectors.toList());
    }
}
