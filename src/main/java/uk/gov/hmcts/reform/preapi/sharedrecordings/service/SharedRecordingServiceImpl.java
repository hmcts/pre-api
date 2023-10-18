package uk.gov.hmcts.reform.preapi.sharedrecordings.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.SharedRecording;
import uk.gov.hmcts.reform.preapi.sharedrecordings.repository.SharedRecordingRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
public class SharedRecordingServiceImpl implements SharedRecordingService {

    @Autowired
    private SharedRecordingRepository sharedRecordingRepository;

    @Transactional
    @Override
    public List<Recording> findAllSharedWithUser(UUID user) {
        return sharedRecordingRepository
            .findByUser_Id(user)
            .stream()
            .map((SharedRecording::getRecording))
            .toList();
    }

    @Transactional
    @Override
    public Optional<Recording> findOneSharedWithUser(UUID recording, UUID user) {
        return sharedRecordingRepository
            .findByUser_IdAndRecording_id(user, recording)
            .map(SharedRecording::getRecording);
    }
}
