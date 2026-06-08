package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;

@StaticMetamodel(TermsAndConditions.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class TermsAndConditions_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String CREATED_AT = "createdAt";
	public static final String TYPE = "type";
	public static final String CONTENT = "content";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.TermsAndConditions#createdAt
	 **/
	public static volatile SingularAttribute<TermsAndConditions, Timestamp> createdAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.TermsAndConditions#type
	 **/
	public static volatile SingularAttribute<TermsAndConditions, TermsAndConditionsType> type;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.TermsAndConditions
	 **/
	public static volatile EntityType<TermsAndConditions> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.TermsAndConditions#content
	 **/
	public static volatile SingularAttribute<TermsAndConditions, String> content;

}

