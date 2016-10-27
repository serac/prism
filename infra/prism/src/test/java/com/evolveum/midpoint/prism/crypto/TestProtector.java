package com.evolveum.midpoint.prism.crypto;

import java.io.IOException;

import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.utils.Base64;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.prism.xml.ns._public.types_3.CipherDataType;
import com.evolveum.prism.xml.ns._public.types_3.EncryptedDataType;
import com.evolveum.prism.xml.ns._public.types_3.EncryptionMethodType;
import com.evolveum.prism.xml.ns._public.types_3.KeyInfoType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import com.ibm.wsdl.extensions.schema.SchemaConstants;

public class TestProtector {
	
	public static final String KEYSTORE_PATH = "src/test/resources/keystore.jceks";
	public static final String KEYSTORE_PASSWORD = "changeit";
	
	private PrismContext prismContext;
	
	private static transient Trace LOGGER = TraceManager.getTrace(TestProtector.class);	
	
	public static Protector createProtector(String xmlCipher){
		AESProtector protector = new AESProtector();
		protector.setKeyStorePassword(KEYSTORE_PASSWORD);
		protector.setKeyStorePath(KEYSTORE_PATH);
		protector.setEncryptionAlgorithm(xmlCipher);
		protector.init();
		return protector;
	}
	
	
  @Test
  public void testProtectorKeyStore() throws Exception{
	  
	
	  String value = "someValue";
	
	  Protector protector256 = createProtector(XMLCipher.AES_256);
	  
	  ProtectedStringType pdt = new ProtectedStringType();
	  pdt.setClearValue(value);
	  protector256.encrypt(pdt);
	  
	  Protector protector128 = createProtector(XMLCipher.AES_128);
	  protector128.decrypt(pdt);
	  
	  AssertJUnit.assertEquals(value, pdt.getClearValue());
	  
	  ProtectedStringType pst = protector256.encryptString(value);
	  String clear = protector256.decryptString(pst);
	  
	  AssertJUnit.assertEquals(value, clear);
	 
  }
}
