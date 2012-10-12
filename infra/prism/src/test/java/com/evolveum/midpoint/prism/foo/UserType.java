//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2012.02.11 at 12:54:37 PM CET 
//


package com.evolveum.midpoint.prism.foo;

import com.evolveum.midpoint.prism.PrismObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;


/**
 * <p>Java class for UserType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UserType">
 *   &lt;complexContent>
 *     &lt;extension base="{http://midpoint.evolveum.com/xml/ns/test/foo-1.xsd}ObjectType">
 *       &lt;sequence>
 *         &lt;element name="fullName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="givenName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="familyName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="additionalNames" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="assignment" type="{http://midpoint.evolveum.com/xml/ns/test/foo-1.xsd}AssignmentType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="activation" type="{http://midpoint.evolveum.com/xml/ns/test/foo-1.xsd}ActivationType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UserType", propOrder = {
    "fullName",
    "givenName",
    "familyName",
    "additionalNames",
    "locality",
    "assignment",
    "activation"
})
public class UserType
    extends ObjectType
    implements Serializable
{

	// This is NOT GENERATED. It is supplied here manually for the testing.
	public final static QName F_FULL_NAME = new QName(NS_FOO, "fullName");
	public final static QName F_GIVEN_NAME = new QName(NS_FOO, "givenName");
	public final static QName F_FAMILY_NAME = new QName(NS_FOO, "familyName");
	public final static QName F_ADDITIONAL_NAMES = new QName(NS_FOO, "additionalNames");
	public final static QName F_ACTIVATION = new QName(NS_FOO, "activation");
	public final static QName F_ASSIGNMENT = new QName(NS_FOO, "assignment");
	public final static QName F_LOCALITY = new QName(NS_FOO, "locality");

    private final static long serialVersionUID = 201202081233L;
    @XmlElement(required = true)
    protected String fullName;
    @XmlElement(required = true)
    protected String givenName;
    @XmlElement(required = true)
    protected String familyName;
    protected List<String> additionalNames;
    protected String locality;
    protected List<AssignmentType> assignment;
    protected ActivationType activation;

    /**
     * Gets the value of the fullName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the value of the fullName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFullName(String value) {
        this.fullName = value;
    }

    /**
     * Gets the value of the givenName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getGivenName() {
        return givenName;
    }

    /**
     * Sets the value of the givenName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setGivenName(String value) {
        this.givenName = value;
    }

    /**
     * Gets the value of the familyName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFamilyName() {
        return familyName;
    }

    /**
     * Sets the value of the familyName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFamilyName(String value) {
        this.familyName = value;
    }

    /**
     * Gets the value of the additionalNames property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the additionalNames property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAdditionalNames().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getAdditionalNames() {
        if (additionalNames == null) {
            additionalNames = new ArrayList<String>();
        }
        return this.additionalNames;
    }
        
    /**
     * Gets the value of the locality property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLocality() {
        return locality;
    }

    /**
     * Sets the value of the locality property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLocality(String value) {
        this.locality = value;
    }

    /**
     * Gets the value of the assignment property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the assignment property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAssignment().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link AssignmentType }
     * 
     * 
     */
    public List<AssignmentType> getAssignment() {
        if (assignment == null) {
            assignment = new ArrayList<AssignmentType>();
        }
        return this.assignment;
    }

    /**
     * Gets the value of the activation property.
     * 
     * @return
     *     possible object is
     *     {@link ActivationType }
     *     
     */
    public ActivationType getActivation() {
        return activation;
    }

    /**
     * Sets the value of the activation property.
     * 
     * @param value
     *     allowed object is
     *     {@link ActivationType }
     *     
     */
    public void setActivation(ActivationType value) {
        this.activation = value;
    }
}
