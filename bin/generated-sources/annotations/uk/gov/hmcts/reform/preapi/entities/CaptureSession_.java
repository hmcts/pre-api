package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

@StaticMetamodel(CaptureSession.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CaptureSession_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String BOOKING = "booking";
	public static final String LIVE_OUTPUT_URL = "liveOutputUrl";
	public static final String ORIGIN = "origin";
	public static final String STARTED_AT = "startedAt";
	public static final String ENCODE_JOBS = "encodeJobs";
	public static final String FINISHED_AT = "finishedAt";
	public static final String DELETED_AT = "deletedAt";
	public static final String STARTED_BY_USER = "startedByUser";
	public static final String FINISHED_BY_USER = "finishedByUser";
	public static final String RECORDINGS = "recordings";
	public static final String INGEST_ADDRESS = "ingestAddress";
	public static final String STATUS = "status";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#booking
	 **/
	public static volatile SingularAttribute<CaptureSession, Booking> booking;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#liveOutputUrl
	 **/
	public static volatile SingularAttribute<CaptureSession, String> liveOutputUrl;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#origin
	 **/
	public static volatile SingularAttribute<CaptureSession, RecordingOrigin> origin;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#startedAt
	 **/
	public static volatile SingularAttribute<CaptureSession, Timestamp> startedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#encodeJobs
	 **/
	public static volatile SetAttribute<CaptureSession, EncodeJob> encodeJobs;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#finishedAt
	 **/
	public static volatile SingularAttribute<CaptureSession, Timestamp> finishedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#deletedAt
	 **/
	public static volatile SingularAttribute<CaptureSession, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#startedByUser
	 **/
	public static volatile SingularAttribute<CaptureSession, User> startedByUser;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#finishedByUser
	 **/
	public static volatile SingularAttribute<CaptureSession, User> finishedByUser;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#recordings
	 **/
	public static volatile SetAttribute<CaptureSession, Recording> recordings;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#ingestAddress
	 **/
	public static volatile SingularAttribute<CaptureSession, String> ingestAddress;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession
	 **/
	public static volatile EntityType<CaptureSession> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.CaptureSession#status
	 **/
	public static volatile SingularAttribute<CaptureSession, RecordingStatus> status;

}

