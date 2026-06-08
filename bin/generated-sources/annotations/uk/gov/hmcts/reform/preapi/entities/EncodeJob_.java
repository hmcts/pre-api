package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;

@StaticMetamodel(EncodeJob.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class EncodeJob_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String JOB_NAME = "jobName";
	public static final String TRANSFORM = "transform";
	public static final String RECORDING_ID = "recordingId";
	public static final String CAPTURE_SESSION = "captureSession";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EncodeJob#jobName
	 **/
	public static volatile SingularAttribute<EncodeJob, String> jobName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EncodeJob#transform
	 **/
	public static volatile SingularAttribute<EncodeJob, EncodeTransform> transform;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EncodeJob#recordingId
	 **/
	public static volatile SingularAttribute<EncodeJob, UUID> recordingId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EncodeJob
	 **/
	public static volatile EntityType<EncodeJob> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EncodeJob#captureSession
	 **/
	public static volatile SingularAttribute<EncodeJob, CaptureSession> captureSession;

}

