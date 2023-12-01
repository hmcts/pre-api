package uk.gov.hmcts.reform.preapi.courts.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.util.UUID;

@Service
public class CourtServiceImpl implements CourtService {

    @Autowired
    private CourtRepository courtRepository;

    @Transactional
    @Override
    public Court findById(UUID id) {
        return courtRepository.findById(id).orElse(null);
    }
}
