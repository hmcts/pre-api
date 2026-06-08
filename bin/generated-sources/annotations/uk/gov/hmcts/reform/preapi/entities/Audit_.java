package uk.gov.hmcts.reform.preapi.entities;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import java.util.UUID;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

@StaticMetamodel(Audit.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Audit_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String CREATED_AT = "createdAt";
	public static final String ACTIVITY = "activity";
	public static final String CREATED_BY = "createdBy";
	public static final String FUNCTIONAL_AREA = "functionalArea";
	public static final String TABLE_RECORD_ID = "tableRecordId";
	public static final String AUDIT_DETAILS = "auditDetails";
	public static final String SOURCE = "source";
	public static final String CATEGORY = "category";
	public static final String TABLE_NAME = "tableName";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#createdAt
	 **/
	public static volatile SingularAttribute<Audit, Timestamp> createdAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#activity
	 **/
	public static volatile SingularAttribute<Audit, String> activity;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#createdBy
	 **/
	public static volatile SingularAttribute<Audit, UUID> createdBy;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#functionalArea
	 **/
	public static volatile SingularAttribute<Audit, String> functionalArea;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#tableRecordId
	 **/
	public static volatile SingularAttribute<Audit, UUID> tableRecordId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#auditDetails
	 **/
	public static volatile SingularAttribute<Audit, JsonNode> auditDetails;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#source
	 **/
	public static volatile SingularAttribute<Audit, AuditLogSource> source;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#category
	 **/
	public static volatile SingularAttribute<Audit, String> category;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit
	 **/
	public static volatile EntityType<Audit> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Audit#tableName
	 **/
	public static volatile SingularAttribute<Audit, String> tableName;

}

