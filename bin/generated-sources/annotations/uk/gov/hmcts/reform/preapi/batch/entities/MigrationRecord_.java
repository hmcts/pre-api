package uk.gov.hmcts.reform.preapi.batch.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import java.util.UUID;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;

@StaticMetamodel(MigrationRecord.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class MigrationRecord_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String REASON = "reason";
	public static final String FILE_NAME = "fileName";
	public static final String RECORDING_VERSION_NUMBER = "recordingVersionNumber";
	public static final String IS_MOST_RECENT = "isMostRecent";
	public static final String CAPTURE_SESSION_ID = "captureSessionId";
	public static final String ARCHIVE_ID = "archiveId";
	public static final String DURATION = "duration";
	public static final String RECORDING_GROUP_KEY = "recordingGroupKey";
	public static final String CREATED_AT = "createdAt";
	public static final String EXHIBIT_REFERENCE = "exhibitReference";
	public static final String PARENT_TEMP_ID = "parentTempId";
	public static final String FILE_SIZE_MB = "fileSizeMb";
	public static final String WITNESS_NAME = "witnessName";
	public static final String COURT_ID = "courtId";
	public static final String RESOLVED_AT = "resolvedAt";
	public static final String ARCHIVE_NAME = "archiveName";
	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String IS_PREFERRED = "isPreferred";
	public static final String BOOKING_ID = "bookingId";
	public static final String DEFENDANT_NAME = "defendantName";
	public static final String URN = "urn";
	public static final String COURT_REFERENCE = "courtReference";
	public static final String CREATE_TIME = "createTime";
	public static final String RECORDING_ID = "recordingId";
	public static final String RECORDING_VERSION = "recordingVersion";
	public static final String STATUS = "status";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#reason
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> reason;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#fileName
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> fileName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#recordingVersionNumber
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> recordingVersionNumber;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#isMostRecent
	 **/
	public static volatile SingularAttribute<MigrationRecord, Boolean> isMostRecent;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#captureSessionId
	 **/
	public static volatile SingularAttribute<MigrationRecord, UUID> captureSessionId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#archiveId
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> archiveId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#duration
	 **/
	public static volatile SingularAttribute<MigrationRecord, Integer> duration;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#recordingGroupKey
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> recordingGroupKey;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#createdAt
	 **/
	public static volatile SingularAttribute<MigrationRecord, Timestamp> createdAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#exhibitReference
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> exhibitReference;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#parentTempId
	 **/
	public static volatile SingularAttribute<MigrationRecord, UUID> parentTempId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#fileSizeMb
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> fileSizeMb;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#witnessName
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> witnessName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord
	 **/
	public static volatile EntityType<MigrationRecord> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#courtId
	 **/
	public static volatile SingularAttribute<MigrationRecord, UUID> courtId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#resolvedAt
	 **/
	public static volatile SingularAttribute<MigrationRecord, Timestamp> resolvedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#archiveName
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> archiveName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#errorMessage
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> errorMessage;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#isPreferred
	 **/
	public static volatile SingularAttribute<MigrationRecord, Boolean> isPreferred;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#bookingId
	 **/
	public static volatile SingularAttribute<MigrationRecord, UUID> bookingId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#defendantName
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> defendantName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#urn
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> urn;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#courtReference
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> courtReference;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#createTime
	 **/
	public static volatile SingularAttribute<MigrationRecord, Timestamp> createTime;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#recordingId
	 **/
	public static volatile SingularAttribute<MigrationRecord, UUID> recordingId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#recordingVersion
	 **/
	public static volatile SingularAttribute<MigrationRecord, String> recordingVersion;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord#status
	 **/
	public static volatile SingularAttribute<MigrationRecord, VfMigrationStatus> status;

}

