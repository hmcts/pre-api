package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.sharedrecordings.service.SharedRecordingService;
import uk.gov.hmcts.reform.preapi.user.service.UserService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/recording")
public class RecordingController {

    @Autowired
    private SharedRecordingService sharedRecordingService;

    @Autowired
    private UserService userService;

    private static final UUID USER_ID = UUID.fromString("26b4398e-72d0-43d6-a0c5-3f16b9629cb6");

    @GetMapping
    public ResponseEntity<List<Recording>> get() {
        // TODO Use headers to get session token and get associated user
        // TODO Logging/Audit here

        Optional<User> user = userService.findById(USER_ID);

        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "entity not found");
        }

        return new ResponseEntity<>(sharedRecordingService.findAllSharedWithUser(USER_ID), HttpStatus.OK);
    }

    @GetMapping("/{recording_id}")
    public ResponseEntity<Recording> get(@PathVariable(name = "recording_id") UUID recordingId) {
        // TODO Use headers to get session token and get associated user
        // TODO Logging/Audit here

        Optional<User> user = userService.findById(USER_ID);

        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "entity not found");
        }

        Optional<Recording> sharedRecording = sharedRecordingService.findOneSharedWithUser(recordingId, USER_ID);
        if (sharedRecording.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "entity not found");
        }

        return new ResponseEntity<>(sharedRecording.get(), HttpStatus.OK);
    }
}
