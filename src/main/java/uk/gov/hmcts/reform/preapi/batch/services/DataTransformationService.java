package uk.gov.hmcts.reform.preapi.batch.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataTransformationService {
    public static final String REDIS_COURTS_KEY = "vf:court:";
    public static final String REDIS_RECORDING_METADATA_KEY = "vf:pre-process:%s-%s-%s";

    private final RedisService redisService;
    private final DataExtractionService extractionService;
    private final CourtRepository courtRepository;
    private final ReferenceDataProcessor referenceDataProcessor;

    private ExtractedMetadata extracted;
    private Map<String, String> sitesDataMap;
    private CSVArchiveListData archiveItem;
    private static final Logger logger = LoggerFactory.getLogger(DataTransformationService.class);

    @Autowired
    public DataTransformationService(
        RedisService redisService,
        DataExtractionService extractionService,
        CourtRepository courtRepository,
        ReferenceDataProcessor referenceDataProcessor
    ) {
        this.redisService = redisService;
        this.extractionService = extractionService;
        this.courtRepository = courtRepository;
        this.referenceDataProcessor = referenceDataProcessor;
    }
    

    public ServiceResult<CleansedData> transformData(CSVArchiveListData archiveItem) {
        try {
            this.archiveItem = archiveItem;
            this.sitesDataMap = getSitesData();

            this.extracted = extractionService.extractMetadata(archiveItem);
            if (extracted.getCourtReference() == ""){
                return ServiceResultUtil.createFailureReponse("Regex matching failed");
            }
            
            CleansedData cleansedData = buildCleansedData();
            return ServiceResultUtil.createSuccessResponse(cleansedData);

        } catch (Exception e) {
            return ServiceResultUtil.createFailureReponse(e.getMessage());
        }
    }


    // ==========================
    // Cleansed Data Construction
    // ==========================
    private CleansedData buildCleansedData() {
        List<Map<String, String>> shareBookingContacts = buildShareBookingContacts();

        String redisKey = RecordingUtils.buildMetadataPreprocessKey(
            extracted.getUrn(),
            extracted.getDefendantLastName(),
            extracted.getWitnessFirstName()
        );

        Map<String, String> existingData = redisService.getHashAll(redisKey, String.class, String.class);

        RecordingUtils.VersionDetails versionDetails = RecordingUtils.processVersioning(
            extracted.getRecordingVersion(),
            extracted.getRecordingVersionNumber(),
            extracted.getUrn(),
            extracted.getDefendantLastName(),
            extracted.getWitnessFirstName(),
            existingData
        );

        return new CleansedData.Builder()
            .setUrn(extracted.getUrn())
            .setExhibitReference(extracted.getExhibitReference())
            .setCaseReference(extracted.createCaseReference())

            .setDefendantLastName(extracted.getDefendantLastName())
            .setWitnessFirstName(extracted.getWitnessFirstName())

            .setCourtReference(extracted.getCourtReference())
            .setCourt(fetchCourtFromDB())
            
            .setRecordingTimestamp(Timestamp.valueOf(extracted.getCreateTime()))
            .setDuration(Duration.ofSeconds(extracted.getDuration()))

            .setState(determineState(shareBookingContacts))
            .setShareBookingContacts(buildShareBookingContacts())

            .setFileExtension(extracted.getFileExtension())
            .setFileName(archiveItem.getFileName())

            .setRecordingVersion(versionDetails.getVersionType())
            .setRecordingVersionNumberStr(versionDetails.getVersionNumberStr())
            .setRecordingVersionNumber(versionDetails.getVersionNumber())
            .setIsMostRecentVersion(versionDetails.isMostRecent())

            .build();
    }


    // ======================
    // Helper Methods
    // ======================
    
    private Court fetchCourtFromDB() {
        String courtReference = extracted.getCourtReference();

        if (courtReference == null || courtReference.isEmpty()) {
            throw new IllegalArgumentException("Court extraction failed for: " + archiveItem.getArchiveName());
        }

        String fullCourtName = sitesDataMap.getOrDefault(courtReference, "Unknown Court");
        String courtIdString = (String) (redisService.getHashValue("vf:court:", fullCourtName, String.class));
        if (courtIdString != null) {
            try {
                UUID courtId = UUID.fromString(courtIdString);
                return courtId   != null ? courtRepository.findById(courtId).orElse(null) : null;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Court ID parsing failed for: " + courtIdString, e);
            }
        }
        return null;
    }


    private List<String[]> getUsersAndEmails(String key) {
        Map<String, List<String[]>> channelUserDataMap = referenceDataProcessor.fetchChannelUserDataMap();
        
        List<String[]> userEmailList = channelUserDataMap.get(key);
        if (userEmailList == null) {
            userEmailList = new ArrayList<>();
        }

        return userEmailList; 
    }

    private List<Map<String, String>> buildShareBookingContacts() {
        String key = archiveItem.getArchiveNameNoExt(); 
        List<String[]> usersAndEmails = getUsersAndEmails(key);
        List<Map<String, String>> contactsList = new ArrayList<>();

        if (usersAndEmails != null) {
            for (String[] userInfo : usersAndEmails) {
                String[] nameParts = userInfo[0].split("\\.");
                Map<String, String> contact = new HashMap<>();
                contact.put("firstName", nameParts.length > 0 ? nameParts[0] : "");
                contact.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
                contact.put("email", userInfo[1]);

                contactsList.add(contact);
            }
        } 

        return contactsList;
    }

    private Map<String, String> getSitesData() {
        Map<String, String> sitesDataMap = redisService.getHashAll("sites_data", String.class, String.class);
        if (sitesDataMap == null || sitesDataMap.isEmpty()) {
            throw new IllegalStateException("Sites data not found in Redis");
        }
        return sitesDataMap;
    }

    private CaseState determineState(List<Map<String, String>> contacts) {
        return (!contacts.isEmpty()) ? CaseState.OPEN : CaseState.CLOSED;
    }

}


