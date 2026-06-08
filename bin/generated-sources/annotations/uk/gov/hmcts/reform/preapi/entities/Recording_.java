package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import java.time.Duration;

@StaticMetamodel(Recording.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Recording_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String DURATION = "duration";
	public static final String CREATED_AT = "createdAt";
	public static final String DELETED_AT = "deletedAt";
	public static final String FILENAME = "filename";
	public static final String RECORDINGS = "recordings";
	public static final String EDIT_INSTRUCTION = "editInstruction";
	public static final String REENCODE = "reencode";
	public static final String EDIT_REQUESTS = "editRequests";
	public static final String PARENT_RECORDING = "parentRecording";
	public static final String CAPTURE_SESSION = "captureSession";
	public static final String VERSION = "version";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#duration
	 **/
	public static volatile SingularAttribute<Recording, Duration> duration;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#createdAt
	 **/
	public static volatile SingularAttribute<Recording, Timestamp> createdAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#deletedAt
	 **/
	public static volatile SingularAttribute<Recording, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#filename
	 **/
	public static volatile SingularAttribute<Recording, String> filename;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#recordings
	 **/
	public static volatile SetAttribute<Recording, Recording> recordings;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#editInstruction
	 **/
	public static volatile SingularAttribute<Recording, String> editInstruction;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#reencode
	 **/
	public static volatile SingularAttribute<Recording, Boolean> reencode;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#editRequests
	 **/
	public static volatile SetAttribute<Recording, EditRequest> editRequests;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#parentRecording
	 **/
	public static volatile SingularAttribute<Recording, Recording> parentRecording;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording
	 **/
	public static volatile EntityType<Recording> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#captureSession
	 **/
	public static volatile SingularAttribute<Recording, CaptureSession> captureSession;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Recording#version
	 **/
	public static volatile SingularAttribute<Recording, Integer> version;

}

