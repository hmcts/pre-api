package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

@StaticMetamodel(EditRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class EditRequest_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String CREATED_BY = "createdBy";
	public static final String EDIT_INSTRUCTION = "editInstruction";
	public static final String SOURCE_RECORDING = "sourceRecording";
	public static final String APPROVED_BY = "approvedBy";
	public static final String STARTED_AT = "startedAt";
	public static final String REJECTION_REASON = "rejectionReason";
	public static final String APPROVED_AT = "approvedAt";
	public static final String JOINTLY_AGREED = "jointlyAgreed";
	public static final String STATUS = "status";
	public static final String FINISHED_AT = "finishedAt";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#createdBy
	 **/
	public static volatile SingularAttribute<EditRequest, User> createdBy;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#editInstruction
	 **/
	public static volatile SingularAttribute<EditRequest, String> editInstruction;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#sourceRecording
	 **/
	public static volatile SingularAttribute<EditRequest, Recording> sourceRecording;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#approvedBy
	 **/
	public static volatile SingularAttribute<EditRequest, String> approvedBy;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#startedAt
	 **/
	public static volatile SingularAttribute<EditRequest, Timestamp> startedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#rejectionReason
	 **/
	public static volatile SingularAttribute<EditRequest, String> rejectionReason;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#approvedAt
	 **/
	public static volatile SingularAttribute<EditRequest, Timestamp> approvedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest
	 **/
	public static volatile EntityType<EditRequest> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#jointlyAgreed
	 **/
	public static volatile SingularAttribute<EditRequest, Boolean> jointlyAgreed;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#status
	 **/
	public static volatile SingularAttribute<EditRequest, EditRequestStatus> status;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.EditRequest#finishedAt
	 **/
	public static volatile SingularAttribute<EditRequest, Timestamp> finishedAt;

}

