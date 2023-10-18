package uk.gov.hmcts.reform.preapi.recordings.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.recordings.repositories.RecordingRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecordingServiceImpl implements RecordingService {

    @Autowired
    private RecordingRepository recordingRepository;

    @Transactional
    @Override
    public List<Recording> get() {
        return recordingRepository.findAll();
    }

    @Transactional
    @Override
    public Optional<Recording> get(UUID id) {
        return recordingRepository.findById(id);
    }
}
